"""
A small in-memory SCIM 2.0 server standing in for Atlassian's user provisioning API during local
development. It implements the subset of SCIM that authentik's SCIM provider uses (ServiceProviderConfig,
Users and Groups with filter, PUT, PATCH and DELETE) and imitates the Atlassian behaviours that matter to
the OpenMRS setup:

- DELETE on a user deactivates it (``active: false``) rather than removing it.
- Groups named in SCIM_MOCK_CONFLICTING_GROUPS behave like groups that already exist in the Atlassian
  organisation but not in the SCIM directory: creating them fails with 409 and filtering for them finds
  nothing, until an admin "approves" the takeover with ``POST /_admin/approve/<name>``. Approval adds the
  group to the directory, keeping the members listed for it, as Atlassian's "Review groups" step does.
  This models documented Atlassian behaviour as closely as we know it; it has not been checked against
  the real API.

State lives only in memory. ``GET /_admin/state`` returns all users, groups and a log of write requests;
``POST /_admin/reset`` clears it. The ``/_admin`` endpoints are unauthenticated, so the server must only be
reachable from the local machine.

Configuration (environment):
  SCIM_MOCK_TOKEN               bearer token clients must send (required)
  SCIM_MOCK_PORT                port to listen on (default 8000)
  SCIM_MOCK_CONFLICTING_GROUPS  JSON object mapping group names to lists of member userNames
"""

import json
import os
import re
import threading
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, unquote, urlparse

USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User"
GROUP_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Group"
LIST_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:ListResponse"
ERROR_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:Error"

TOKEN = os.environ["SCIM_MOCK_TOKEN"]
PORT = int(os.environ.get("SCIM_MOCK_PORT", "8000"))

FILTER_RE = re.compile(r'^\s*(\w+)\s+eq\s+"(.*)"\s*$', re.IGNORECASE)
MEMBER_PATH_RE = re.compile(r'^members\[value eq "(.+)"\]$', re.IGNORECASE)

lock = threading.Lock()
state = {}


def reset():
    state.clear()
    state.update(
        users={},
        groups={},
        conflicting=json.loads(os.environ.get("SCIM_MOCK_CONFLICTING_GROUPS", "{}")),
        log=[],
    )


class ScimError(Exception):
    def __init__(self, status, detail):
        super().__init__(detail)
        self.status = status
        self.detail = detail


def find(collection, attr, value):
    for resource in collection.values():
        if str(resource.get(attr, "")).lower() == value.lower():
            return resource
    return None


def user_by_email(email):
    for user in state["users"].values():
        if any(e.get("value", "").lower() == email.lower() for e in user.get("emails", [])):
            return user
    return None


def store_user(body, user_id):
    if not body.get("userName"):
        raise ScimError(400, "userName is required")
    clash = find(state["users"], "userName", body["userName"])
    for email in body.get("emails", []):
        clash = clash or user_by_email(email.get("value", ""))
    if clash and clash["id"] != user_id:
        raise ScimError(409, "A user with this userName or email already exists in the directory")
    user = {k: v for k, v in body.items() if k not in ("id", "meta")}
    user.setdefault("active", True)
    user.update(id=user_id, schemas=[USER_SCHEMA], meta={"resourceType": "User"})
    state["users"][user_id] = user
    return user


def store_group(body, group_id, members=None):
    name = body.get("displayName")
    if not name:
        raise ScimError(400, "displayName is required")
    if name.lower() in (n.lower() for n in state["conflicting"]):
        raise ScimError(409, f"Group '{name}' already exists in the organisation and needs review")
    clash = find(state["groups"], "displayName", name)
    if clash and clash["id"] != group_id:
        raise ScimError(409, "A group with this displayName already exists in the directory")
    group = {k: v for k, v in body.items() if k not in ("id", "meta", "members")}
    if members is None:
        members = body.get("members", [])
    group.update(
        id=group_id,
        schemas=[GROUP_SCHEMA],
        meta={"resourceType": "Group"},
        members=[{"value": m["value"]} for m in members],
    )
    state["groups"][group_id] = group
    return group


def patch_group(group, operations):
    members = {m["value"] for m in group["members"]}
    for op in operations:
        kind = op.get("op", "").lower()
        path = op.get("path")
        value = op.get("value")
        if path is None and kind in ("add", "replace"):
            group.update({k: v for k, v in value.items() if k not in ("id", "members")})
            if "members" in value:
                members = {m["value"] for m in value["members"]}
        elif path and path.lower() == "members" and kind == "add":
            members |= {m["value"] for m in value}
        elif path and path.lower() == "members" and kind == "replace":
            members = {m["value"] for m in value}
        elif path and path.lower() == "members" and kind == "remove":
            members -= {m["value"] for m in value} if value else members
        elif path and (match := MEMBER_PATH_RE.match(path)) and kind == "remove":
            members.discard(match.group(1))
        elif path and kind in ("add", "replace"):
            group[path] = value
        else:
            raise ScimError(400, f"Unsupported PATCH operation: {op}")
    unknown = members - state["users"].keys()
    if unknown:
        raise ScimError(400, f"Unknown member ids: {sorted(unknown)}")
    group["members"] = [{"value": m} for m in sorted(members)]
    return group


def list_response(resources, query):
    if "filter" in query:
        match = FILTER_RE.match(query["filter"][0])
        if not match:
            raise ScimError(400, "Only 'attr eq \"value\"' filters are supported")
        attr, value = match.groups()
        resources = [r for r in resources if str(r.get(attr, "")).lower() == value.lower()]
    start = int(query.get("startIndex", ["1"])[0])
    count = int(query.get("count", ["100"])[0])
    page = resources[start - 1 : start - 1 + count]
    return {
        "schemas": [LIST_SCHEMA],
        "totalResults": len(resources),
        "startIndex": start,
        "itemsPerPage": len(page),
        "Resources": page,
    }


def service_provider_config():
    return {
        "schemas": ["urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"],
        "patch": {"supported": True},
        "bulk": {"supported": False, "maxOperations": 0, "maxPayloadSize": 0},
        "filter": {"supported": True, "maxResults": 100},
        "changePassword": {"supported": False},
        "sort": {"supported": False},
        "etag": {"supported": False},
        "authenticationSchemes": [],
    }


def handle_scim(method, parts, query, body):
    """Dispatches a request whose path ends in ServiceProviderConfig, Users[/id] or Groups[/id]."""
    if parts[-1] == "ServiceProviderConfig" and method == "GET":
        return 200, service_provider_config()
    if parts[-1] in ("Users", "Groups"):
        kind, resource_id = parts[-1], None
    elif len(parts) >= 2 and parts[-2] in ("Users", "Groups"):
        kind, resource_id = parts[-2], parts[-1]
    else:
        raise ScimError(404, "Not found")
    collection = state["users"] if kind == "Users" else state["groups"]

    if resource_id is None:
        if method == "GET":
            return 200, list_response(list(collection.values()), query)
        if method == "POST":
            new_id = str(uuid.uuid4())
            resource = store_user(body, new_id) if kind == "Users" else store_group(body, new_id)
            return 201, resource
        raise ScimError(405, "Method not allowed")

    resource = collection.get(resource_id)
    if resource is None:
        raise ScimError(404, f"{kind[:-1]} {resource_id} not found")
    if method == "GET":
        return 200, resource
    if method == "PUT":
        if kind == "Users":
            return 200, store_user(body, resource_id)
        return 200, store_group(body, resource_id)
    if method == "PATCH" and kind == "Groups":
        return 200, patch_group(resource, body.get("Operations", []))
    if method == "DELETE":
        if kind == "Users":
            resource["active"] = False
        else:
            del collection[resource_id]
        return 204, None
    raise ScimError(405, "Method not allowed")


def approve_conflict(name):
    """Moves a conflicting group into the directory, as approving it in Atlassian's admin UI does."""
    key = next((n for n in state["conflicting"] if n.lower() == name.lower()), None)
    if key is None:
        raise ScimError(404, f"No pending conflict for group '{name}'")
    member_names = state["conflicting"].pop(key)
    members = []
    for user_name in member_names:
        user = find(state["users"], "userName", user_name)
        if user is None:
            user = store_user({"userName": user_name, "emails": [{"value": user_name, "primary": True}]},
                              str(uuid.uuid4()))
        members.append({"value": user["id"]})
    return 200, store_group({"displayName": key}, str(uuid.uuid4()), members)


class Handler(BaseHTTPRequestHandler):
    def _send(self, status, payload):
        self.send_response(status)
        if payload is None:
            self.end_headers()
            return
        data = json.dumps(payload, indent=2).encode()
        self.send_header("Content-Type", "application/scim+json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _dispatch(self):
        url = urlparse(self.path)
        parts = [unquote(p) for p in url.path.split("/") if p]
        query = parse_qs(url.query)
        length = int(self.headers.get("Content-Length") or 0)
        body = json.loads(self.rfile.read(length)) if length else {}
        method = self.command
        try:
            with lock:
                if parts[:1] == ["_admin"]:
                    if parts[1:] == ["state"] and method == "GET":
                        status, payload = 200, state
                    elif parts[1:] == ["reset"] and method == "POST":
                        reset()
                        status, payload = 204, None
                    elif parts[1:2] == ["approve"] and len(parts) == 3 and method == "POST":
                        status, payload = approve_conflict(parts[2])
                    else:
                        raise ScimError(404, "Not found")
                else:
                    if self.headers.get("Authorization") != f"Bearer {TOKEN}":
                        raise ScimError(401, "Invalid or missing bearer token")
                    status, payload = handle_scim(method, parts, query, body)
                    if method != "GET":
                        state["log"].append({"method": method, "path": url.path, "status": status, "body": body})
        except ScimError as exc:
            status, payload = exc.status, {"schemas": [ERROR_SCHEMA], "status": str(exc.status), "detail": exc.detail}
            if parts[:1] != ["_admin"] and method != "GET":
                with lock:
                    state["log"].append({"method": method, "path": url.path, "status": status, "body": body})
        self._send(status, payload)

    do_GET = do_POST = do_PUT = do_PATCH = do_DELETE = _dispatch


if __name__ == "__main__":
    reset()
    print(f"SCIM mock listening on :{PORT}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
