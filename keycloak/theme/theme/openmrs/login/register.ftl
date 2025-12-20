<#import "template.ftl" as layout>
<#import "components/atoms/button.ftl" as button>
<#import "components/atoms/button-group.ftl" as buttonGroup>
<#import "components/atoms/form.ftl" as form>
<#import "components/atoms/input.ftl" as input>
<#import "components/atoms/link.ftl" as link>

<@layout.registrationLayout
  displayMessage=!messagesPerField.existsError("firstName", "lastName", "email", "username", "password", "password-confirm")
  ;
  section
>
  <#if section="header">
    ${msg("registerTitle")}
  <#elseif section="form">
    <@form.kw action=url.registrationAction method="post">
      <@input.kw
        autocomplete="given-name"
        autofocus=true
        invalid=messagesPerField.existsError("firstName")
        label=msg("firstName")
        message=kcSanitize(messagesPerField.get("firstName"))
        name="firstName"
        type="text"
        value=(register.formData.firstName)!''
      />
      <@input.kw
        autocomplete="family-name"
        invalid=messagesPerField.existsError("lastName")
        label=msg("lastName")
        message=kcSanitize(messagesPerField.get("lastName"))
        name="lastName"
        type="text"
        value=(register.formData.lastName)!''
      />
      <@input.kw
        autocomplete="email"
        invalid=messagesPerField.existsError("email")
        label=msg("email")
        message=kcSanitize(messagesPerField.get("email"))
        name="email"
        type="email"
        value=(register.formData.email)!''
      />
      <#if !realm.registrationEmailAsUsername>
        <@input.kw
          autocomplete="username"
          invalid=messagesPerField.existsError("username")
          label=msg("username")
          message=kcSanitize(messagesPerField.get("username"))
          name="username"
          type="text"
          value=(register.formData.username)!''
        />
      </#if>
      <#if passwordRequired??>
        <div class="relative">
          <@input.kw
            autocomplete="new-password"
            invalid=messagesPerField.existsError("password", "password-confirm")
            label=msg("password")
            message=kcSanitize(messagesPerField.getFirstError("password", "password-confirm"))
            name="password"
            type="password"
            id="password-input"
          />
          <div class="absolute inset-y-0 right-0 pr-3 flex items-center text-sm leading-5">
            <svg id="eye-icon" class="h-6 w-6 text-gray-700 cursor-pointer" fill="none" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
              <path stroke-linecap="round" stroke-linejoin="round" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
            </svg>
            <svg id="eye-slashed-icon" class="h-6 w-6 text-gray-700 cursor-pointer hidden" fill="none" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.542-7 1.274-4.057 5.064 7 9.542 7 .847 0 1.67.127 2.452.364m-6.048 9.944a3 3 0 11-4.243-4.242m4.243 4.242L18.75 21 15 15.25M3.25 3.25l17.5 17.5" />
            </svg>
          </div>
        </div>
        <div class="relative">
          <@input.kw
            autocomplete="new-password"
            invalid=messagesPerField.existsError("password-confirm")
            label=msg("passwordConfirm")
            message=kcSanitize(messagesPerField.get("password-confirm"))
            name="password-confirm"
            type="password"
            id="password-confirm-input"
          />
          <div class="absolute inset-y-0 right-0 pr-3 flex items-center text-sm leading-5">
            <svg id="eye-icon-confirm" class="h-6 w-6 text-gray-700 cursor-pointer" fill="none" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
              <path stroke-linecap="round" stroke-linejoin="round" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
            </svg>
            <svg id="eye-slashed-icon-confirm" class="h-6 w-6 text-gray-700 cursor-pointer hidden" fill="none" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.542-7 1.274-4.057 5.064 7 9.542 7 .847 0 1.67.127 2.452.364m-6.048 9.944a3 3 0 11-4.243-4.242m4.243 4.242L18.75 21 15 15.25M3.25 3.25l17.5 17.5" />
            </svg>
          </div>
        </div>
      </#if>
      <#if recaptchaRequired??>
        <div class="g-recaptcha" data-sitekey="${recaptchaSiteKey}" data-size="compact"></div>
      </#if>
      <@buttonGroup.kw>
        <@button.kw color="primary" type="submit">
          ${msg("doRegister")}
        </@button.kw>
      </@buttonGroup.kw>
    </@form.kw>
  <#elseif section="nav">
    <@link.kw color="secondary" href=url.loginUrl size="small">
      ${kcSanitize(msg("backToLogin"))?no_esc}
    </@link.kw>
  </#if>
</@layout.registrationLayout>
<script src="https://www.google.com/recaptcha/api.js" async defer></script>
<script>
  const passwordInput = document.getElementById('password-input');
  const eyeIcon = document.getElementById('eye-icon');
  const eyeSlashedIcon = document.getElementById('eye-slashed-icon');

  const passwordConfirmInput = document.getElementById('password-confirm-input');
  const eyeIconConfirm = document.getElementById('eye-icon-confirm');
  const eyeSlashedIconConfirm = document.getElementById('eye-slashed-icon-confirm');


  eyeIcon.addEventListener('click', () => {
    passwordInput.type = 'text';
    eyeIcon.classList.add('hidden');
    eyeSlashedIcon.classList.remove('hidden');
  });

  eyeSlashedIcon.addEventListener('click', () => {
    passwordInput.type = 'password';
    eyeSlashedIcon.classList.add('hidden');
    eyeIcon.classList.remove('hidden');
  });

  eyeIconConfirm.addEventListener('click', () => {
    passwordConfirmInput.type = 'text';
    eyeIconConfirm.classList.add('hidden');
    eyeSlashedIconConfirm.classList.remove('hidden');
  });

  eyeSlashedIconConfirm.addEventListener('click', () => {
    passwordConfirmInput.type = 'password';
    eyeSlashedIconConfirm.classList.add('hidden');
    eyeIconConfirm.classList.remove('hidden');
  });
</script>