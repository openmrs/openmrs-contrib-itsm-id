import Keycloak from 'keycloak-js';

const config = window.__OPENMRS_ACCOUNT__;

const spinner = document.getElementById('spinner-screen')!;
const landing = document.getElementById('openmrs-landing')!;
const app = document.getElementById('app')!;
const joinSection = document.getElementById('join-or-sign-section')!;
const actions = document.getElementById('actions-section')!;
const signInBtn = document.getElementById('landing-sign-in') as HTMLButtonElement;
const signOutBtn = document.getElementById('landing-sign-out') as HTMLButtonElement;
const registerBtn = document.getElementById('landing-register') as HTMLButtonElement;
const signInInlineBtn = document.getElementById('landing-sign-in-inline') as HTMLButtonElement;

document.querySelectorAll<HTMLElement>('.openmrs-landing__card').forEach((card) => {
  const links = card.querySelectorAll<HTMLElement>('.openmrs-landing__card-link');
  links.forEach((link) => {
    const flag = link.dataset.visible;
    if (flag && window.__openmrsFeatures[flag] === false) {
      link.hidden = true;
    }
    link.addEventListener('click', () => navigateToConsole(link.dataset.path ?? ''));
  });
  const visibleLinks = Array.from(links).filter((l) => !l.hidden);
  if (visibleLinks.length === 0) {
    card.hidden = true;
  }
});

let reactLoaded = false;
function loadReactEntry() {
  if (reactLoaded || !config.entryScript) return;
  reactLoaded = true;
  import(/* @vite-ignore */ config.entryScript);
}

function navigateToConsole(path: string) {
  if (path) {
    window.location.hash = '/' + path;
  }
  landing.hidden = true;
  spinner.hidden = true;
  app.hidden = false;
  loadReactEntry();
}

const query = new URLSearchParams(window.location.search);
const isConsoleCallback = query.has('state') && (query.has('code') || query.has('error'));

if (window.location.hash.startsWith('#/') || isConsoleCallback) {
  navigateToConsole('');
} else {
  const keycloak = new Keycloak({
    url: config.authUrl,
    realm: config.realm,
    clientId: 'account-console',
  });
  window.keycloak = keycloak;

  const showLanding = (authenticated: boolean) => {
    spinner.hidden = true;
    landing.hidden = false;
    if (authenticated) {
      actions.hidden = false;
      signOutBtn.hidden = false;
    } else {
      joinSection.hidden = false;
      signInBtn.hidden = false;
    }
  };

  signInBtn.addEventListener('click', () => keycloak.login());
  signInInlineBtn.addEventListener('click', () => keycloak.login());
  signOutBtn.addEventListener('click', () => keycloak.logout());
  registerBtn.addEventListener('click', () => keycloak.register());

  keycloak
    .init({ onLoad: 'check-sso', pkceMethod: 'S256' })
    .then((authenticated) => showLanding(authenticated))
    .catch(() => showLanding(false));
}
