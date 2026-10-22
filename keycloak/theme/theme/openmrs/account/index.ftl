<!doctype html>
<html lang="${locale!'en'}" dir="${(localeDir!'ltr')}">
  <head>
    <meta charset="utf-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="robots" content="noindex, nofollow">
    <meta name="color-scheme" content="light${darkMode?then(' dark', '')}">
    <title>${msg("accountManagementTitle")}</title>

    <link rel="icon" type="image/x-icon" href="${resourceUrl}${properties.favIcon!'/public/favicon.png'}">

    <script type="importmap">
      {
        "imports": {
          "react": "${resourceCommonUrl}/vendor/react/react.production.min.js",
          "react/jsx-runtime": "${resourceCommonUrl}/vendor/react/react-jsx-runtime.production.min.js",
          "react-dom": "${resourceCommonUrl}/vendor/react-dom/react-dom.production.min.js"
        }
      }
    </script>

    <#if darkMode>
      <script type="module" async blocking="render">
        const DARK_MODE_CLASS = "${properties.kcDarkModeClass!'pf-v5-theme-dark'}";
        const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
        updateDarkMode(mediaQuery.matches);
        mediaQuery.addEventListener("change", (event) => updateDarkMode(event.matches));
        function updateDarkMode(isEnabled) {
          const { classList } = document.documentElement;
          if (isEnabled) {
            classList.add(DARK_MODE_CLASS);
          } else {
            classList.remove(DARK_MODE_CLASS);
          }
        }
      </script>
    </#if>

    <#if !isSecureContext>
      <script type="module" src="${resourceCommonUrl}/vendor/web-crypto-shim/web-crypto-shim.js"></script>
    </#if>

    <#if entryStyles?has_content>
      <#list entryStyles as style>
        <link rel="stylesheet" href="${resourceUrl}/${style}">
      </#list>
    </#if>
    <#if entryImports?has_content>
      <#list entryImports as import>
        <link rel="modulepreload" href="${resourceUrl}/${import}">
      </#list>
    </#if>

    <#if properties.styles?has_content>
      <#list properties.styles?split(' ') as style>
        <link rel="stylesheet" href="${resourceUrl}/${style}">
      </#list>
    </#if>

    <style>
      :root {
        --openmrs-primary: #027d77;
        --openmrs-primary-hover: #016b66;
        --openmrs-topbar-bg: #333;
        --openmrs-text: #151515;
        --openmrs-page-bg: #fff;
        --openmrs-card-bg: #fff;
        --openmrs-card-border: #d2d2d2;
        --openmrs-footer-bg: #f5f5f5;
        --openmrs-link: #06c;
      }

      /* Dark mode is enabled by v3's bootstrap script adding pf-v5-theme-dark
         to <html> when prefers-color-scheme: dark. PF5 redefines its own
         tokens scoped to that class but doesn't expose top-level page/text
         colors usable from a custom landing, so we maintain our own pair. */
      :root.pf-v5-theme-dark {
        --openmrs-text: #e0e0e0;
        --openmrs-page-bg: #1b1d21;
        --openmrs-card-bg: #292929;
        --openmrs-card-border: #444;
        --openmrs-footer-bg: #151515;
        --openmrs-link: #73bcf7;
      }

      body, html {
        margin: 0;
        padding: 0;
        min-height: 100vh;
        font-family: var(--pf-v5-global--FontFamily--text, "RedHatText", "Open Sans", helvetica, arial, sans-serif);
        color: var(--openmrs-text);
        background: var(--openmrs-page-bg);
      }

      [hidden] {
        display: none !important;
      }

      .openmrs-spinner-screen {
        min-height: 100vh;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        text-align: center;
      }
      .openmrs-spinner-screen img {
        height: 60px;
        margin-bottom: 1rem;
      }

      .openmrs-landing {
        display: flex;
        flex-direction: column;
        min-height: 100vh;
      }
      .openmrs-landing__header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 1rem 2rem;
        background: var(--openmrs-topbar-bg);
        color: #fff;
      }
      .openmrs-landing__brand img {
        height: 35px;
        display: block;
      }
      .openmrs-landing__header-actions {
        display: flex;
        gap: 0.5rem;
        align-items: center;
      }
      .openmrs-landing__main {
        flex: 1;
        max-width: 1200px;
        margin: 0 auto;
        padding: 2rem 1.5rem;
        width: 100%;
        box-sizing: border-box;
      }
      .openmrs-landing__welcome h1 {
        margin: 0 0 0.5rem;
        font-size: 1.75rem;
      }
      .openmrs-landing__welcome p {
        max-width: 70ch;
        line-height: 1.5;
      }
      .openmrs-landing__language-note {
        max-width: 70ch;
        padding: 0.75rem 1rem;
        background: var(--openmrs-card-bg);
        border: 1px solid var(--openmrs-card-border);
        border-radius: 4px;
        line-height: 1.5;
      }
      .openmrs-landing__cta {
        margin-top: 1.25rem;
        display: flex;
        align-items: center;
        gap: 0.5rem;
      }
      .openmrs-landing__cards {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
        gap: 1rem;
        margin-top: 2rem;
      }
      .openmrs-landing__card {
        background: var(--openmrs-card-bg);
        border: 1px solid var(--openmrs-card-border);
        border-radius: 4px;
        padding: 1.25rem 1.5rem;
        display: flex;
        flex-direction: column;
      }
      .openmrs-landing__card h2 {
        margin: 0 0 0.75rem;
        font-size: 1.15rem;
      }
      .openmrs-landing__card-link {
        display: inline-block;
        padding: 0.25rem 0;
        color: var(--openmrs-link);
        text-decoration: none;
        cursor: pointer;
        background: none;
        border: 0;
        font: inherit;
        text-align: left;
      }
      .openmrs-landing__card-link:hover {
        text-decoration: underline;
      }
      .openmrs-landing__footer {
        padding: 1.5rem 2rem;
        background: var(--openmrs-footer-bg);
        border-top: 1px solid var(--openmrs-card-border);
      }
      .openmrs-landing__footer h2 {
        margin: 0 0 0.5rem;
        font-size: 1.1rem;
      }
      .openmrs-landing__footer p {
        margin: 0;
        max-width: 70ch;
        line-height: 1.5;
      }

      .openmrs-btn {
        display: inline-block;
        padding: 0.5rem 1rem;
        background: var(--openmrs-primary);
        color: #fff;
        border: 0;
        border-radius: 3px;
        font-size: 1rem;
        font-weight: 500;
        cursor: pointer;
        text-decoration: none;
        line-height: 1.2;
      }
      .openmrs-btn:hover {
        background: var(--openmrs-primary-hover);
        color: #fff;
      }

      .openmrs-topnav {
        display: flex;
        flex-wrap: wrap;
        background: var(--openmrs-topbar-bg);
        padding: 0 0.5rem;
      }
      .openmrs-topnav a {
        color: #f2f2f2;
        text-decoration: none;
        padding: 0.25rem 1rem;
        font-size: 0.875rem;
        line-height: 1.5;
      }
      .openmrs-topnav a:hover {
        background: #ddd;
        color: #000;
      }
      .openmrs-topnav a.active {
        background: var(--openmrs-primary);
        color: #fff;
      }
    </style>
  </head>

  <body data-page-id="account">
    <nav class="openmrs-topnav" aria-label="${msg("accountSiteNavigation")}">
      <a target="_parent" href="https://openmrs.org">OpenMRS.org</a>
      <a class="active" href="#">ID</a>
      <a target="_parent" href="https://addons.openmrs.org">Addons</a>
      <a target="_parent" href="https://wiki.openmrs.org">Wiki</a>
      <a target="_parent" href="https://atlas.openmrs.org">Atlas</a>
      <a target="_parent" href="https://talk.openmrs.org/c/ask">Ask OpenMRS</a>
      <a target="_parent" href="https://talk.openmrs.org">Talk</a>
      <a target="_parent" href="https://slack.openmrs.org">Slack</a>
      <a target="_parent" href="https://issues.openmrs.org">Issues</a>
      <a target="_parent" href="https://github.com/OpenMRS">Code</a>
      <a target="_parent" href="https://ci.openmrs.org">CI</a>
    </nav>

    <div id="spinner-screen" class="openmrs-spinner-screen">
      <img src="${resourceUrl}${properties.logo!'/public/logo-white.png'}" alt="OpenMRS">
      <p>${msg("loadingMessage")}</p>
      <svg xmlns="http://www.w3.org/2000/svg" width="80" height="80" viewBox="0 0 100 100" aria-hidden="true">
        <path d="M10 50A40 40 0 0 0 90 50A40 42 0 0 1 10 50" fill="#5DBCD2" stroke="none">
          <animateTransform attributeName="transform" type="rotate" dur="1s" repeatCount="indefinite" values="0 50 51;360 50 51"/>
        </path>
      </svg>
    </div>

    <div id="openmrs-landing" class="openmrs-landing" hidden>
      <header class="openmrs-landing__header">
        <a class="openmrs-landing__brand" href="${properties.logoUrl!baseUrl}">
          <img src="${resourceUrl}${properties.logo!'/public/logo-white.png'}" alt="OpenMRS">
        </a>
        <div class="openmrs-landing__header-actions">
          <button id="landing-sign-in" class="openmrs-btn" hidden type="button">${msg("doSignIn")}</button>
          <button id="landing-sign-out" class="openmrs-btn" hidden type="button">${msg("doSignOut")}</button>
        </div>
      </header>

      <main class="openmrs-landing__main">
        <section class="openmrs-landing__welcome">
          <h1>${msg("accountManagementWelcomeMessage")}</h1>
          <p>${msg("accountWelcomeIntro", '<a href="https://wiki.openmrs.org/display/ISM/OpenMRS+ID">', '</a>')?no_esc}</p>
          <#if !(locale!'en')?lower_case?starts_with("en")>
            <p class="openmrs-landing__language-note">${msg("accountEnglishCommunityNote")}</p>
          </#if>
          <div id="join-or-sign-section" class="openmrs-landing__cta" hidden>
            <button id="landing-register" class="openmrs-btn" type="button">${msg("joinCommunity")}</button>
            <span>${msg("accountWelcomeOr")}</span>
            <button id="landing-sign-in-inline" class="openmrs-btn" type="button">${msg("doSignIn")}</button>
          </div>
        </section>

        <section id="actions-section" hidden>
          <div class="openmrs-landing__cards">
            <#assign content=theme.apply("content.json")?eval>
            <#list content as item>
              <div class="openmrs-landing__card" data-card-id="${item.label}">
                <h2>${msg(item.label)}</h2>
                <#if item.children??>
                  <#list item.children as sub>
                    <button class="openmrs-landing__card-link" type="button"
                            data-path="${sub.path!''}"
                            data-visible="${sub.isVisible!''}">${msg(sub.label)}</button>
                  </#list>
                <#else>
                  <button class="openmrs-landing__card-link" type="button"
                          data-path="${item.path!''}"
                          data-visible="${item.isVisible!''}">${msg(item.label)}</button>
                </#if>
              </div>
            </#list>
          </div>
        </section>
      </main>

      <footer class="openmrs-landing__footer">
        <h2>${msg("accountHelpTitle")}</h2>
        <p>${msg("accountHelpText", '<a href="http://om.rs/helpdesk">', '</a>')?no_esc}</p>
      </footer>
    </div>

    <div id="app" hidden>
      <main class="container">
        <div class="keycloak__loading-container">
          <svg class="pf-v5-c-spinner pf-m-xl" role="progressbar" aria-valuetext="${msg("accountSpinnerValueText")}" viewBox="0 0 100 100" aria-label="${msg("accountSpinnerLabel")}">
            <circle class="pf-v5-c-spinner__path" cx="50" cy="50" r="45" fill="none"></circle>
          </svg>
          <p id="loading-text">${msg("loadingMessage")}</p>
        </div>
      </main>
    </div>

    <noscript>${msg("accountNoScript")}</noscript>

    <script id="environment" type="application/json">
      {
        "serverBaseUrl": "${serverBaseUrl}",
        "authUrl": "${authUrl}",
        "authServerUrl": "${authServerUrl}",
        "realm": "${realm.name}",
        "clientId": "${clientId}",
        "resourceUrl": "${resourceUrl}",
        "logo": "${properties.logo!""}",
        "logoUrl": "${properties.logoUrl!""}",
        "baseUrl": "${baseUrl}",
        "locale": "${locale}",
        "referrerName": "${referrerName!""}",
        "referrerUrl": "${referrer_uri!""}",
        "features": {
          "isRegistrationEmailAsUsername": ${realm.registrationEmailAsUsername?c},
          "isEditUserNameAllowed": ${realm.editUsernameAllowed?c},
          "isInternationalizationEnabled": ${realm.isInternationalizationEnabled()?c},
          "isLinkedAccountsEnabled": ${isLinkedAccountsEnabled?c},
          "isMyResourcesEnabled": ${(realm.userManagedAccessAllowed && isAuthorizationEnabled)?c},
          "isViewOrganizationsEnabled": ${isViewOrganizationsEnabled?c},
          "deleteAccountAllowed": ${deleteAccountAllowed?c},
          "updateEmailFeatureEnabled": ${updateEmailFeatureEnabled?c},
          "updateEmailActionEnabled": ${updateEmailActionEnabled?c},
          "isViewGroupsEnabled": ${isViewGroupsEnabled?c},
          "isOid4VciEnabled": ${isOid4VciEnabled?c}
        },
        "scope": "${scope!""}"
      }
    </script>

    <script>
      // FreeMarker → JS bridge: feature flags gate card visibility, and the
      // bundled landing module needs the auth-server coordinates + the
      // (content-hashed) React entry path it should lazy-load on card click.
      window.__openmrsFeatures = {
        isLinkedAccountsEnabled: ${isLinkedAccountsEnabled?c},
        isMyResourcesEnabled: ${(realm.userManagedAccessAllowed && isAuthorizationEnabled)?c},
        isViewOrganizationsEnabled: ${isViewOrganizationsEnabled?c},
        isViewGroupsEnabled: ${isViewGroupsEnabled?c},
        isOid4VciEnabled: ${isOid4VciEnabled?c}
      };
      window.__OPENMRS_ACCOUNT__ = {
        authUrl: '${authUrl}',
        realm: '${realm.name}',
        entryScript: <#if entryScript?has_content>'${resourceUrl}/${entryScript}'<#else>null</#if>
      };
    </script>
    <script type="module" src="${resourceUrl}/dist/landing.js"></script>
  </body>
</html>
