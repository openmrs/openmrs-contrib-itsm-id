<#--
  Cloudflare Turnstile widget; renders only when Turnstile is configured. When the realm is
  internationalized, the widget uses the page's language, which must be one Cloudflare supports;
  otherwise Cloudflare picks one from the browser.
-->
<#macro kw>
  <#if turnstileSiteKey??>
    <div
      class="cf-turnstile"
      data-sitekey="${turnstileSiteKey}"
      data-size="flexible"
      <#if locale??>data-language="${locale.currentLanguageTag?lower_case}"</#if>
    ></div>
    <script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>
  </#if>
</#macro>
