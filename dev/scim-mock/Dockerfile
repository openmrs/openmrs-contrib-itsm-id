# Local-development stand-in for Atlassian's SCIM API. Not for staging or production.
FROM python:3.14-alpine3.24

COPY scim_mock.py /app/scim_mock.py

EXPOSE 8000

CMD ["python", "-u", "/app/scim_mock.py"]
