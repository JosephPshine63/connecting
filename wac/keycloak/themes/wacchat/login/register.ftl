<!DOCTYPE html>
<html lang="<#if locale??>${locale.currentLanguageTag}<#else>en</#if>">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="robots" content="noindex, nofollow">
    <title>${msg("registerTitle")}</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap">
    <link rel="stylesheet" href="${url.resourcesPath}/css/login.css">
</head>
<body>

<div class="wac-wrap">
  <div class="wac-card wac-card--wide">

    <div class="wac-logo">WacChat</div>
    <p class="wac-page-subtitle">${msg("registerTitle")}</p>

    <#if message?has_content>
      <div class="wac-alert wac-alert-${message.type}" role="alert">
        ${kcSanitize(message.summary)?no_esc}
      </div>
    </#if>

    <form id="kc-register-form" action="${url.registrationAction}" method="post" novalidate>

      <div class="wac-row-2">
        <div class="wac-field">
          <label for="firstName">${msg("firstName")} <span class="wac-required">*</span></label>
          <input id="firstName" name="firstName" type="text"
                 value="${(register.formData.firstName!'')}"
                 autocomplete="given-name"
                 <#if messagesPerField.existsError('firstName')>class="wac-input-error"</#if>>
          <#if messagesPerField.existsError('firstName')>
            <span class="wac-field-msg wac-field-msg--error">
              ${kcSanitize(messagesPerField.get('firstName'))?no_esc}
            </span>
          </#if>
        </div>

        <div class="wac-field">
          <label for="lastName">${msg("lastName")} <span class="wac-required">*</span></label>
          <input id="lastName" name="lastName" type="text"
                 value="${(register.formData.lastName!'')}"
                 autocomplete="family-name"
                 <#if messagesPerField.existsError('lastName')>class="wac-input-error"</#if>>
          <#if messagesPerField.existsError('lastName')>
            <span class="wac-field-msg wac-field-msg--error">
              ${kcSanitize(messagesPerField.get('lastName'))?no_esc}
            </span>
          </#if>
        </div>
      </div>

      <div class="wac-field">
        <label for="email">${msg("email")} <span class="wac-required">*</span></label>
        <input id="email" name="email" type="email"
               value="${(register.formData.email!'')}"
               autocomplete="email"
               <#if messagesPerField.existsError('email')>class="wac-input-error"</#if>>
        <#if messagesPerField.existsError('email')>
          <span class="wac-field-msg wac-field-msg--error">
            ${kcSanitize(messagesPerField.get('email'))?no_esc}
          </span>
        </#if>
      </div>

      <#if passwordRequired??>
        <div class="wac-field">
          <label for="password">${msg("password")} <span class="wac-required">*</span></label>
          <div class="wac-password-wrap">
            <input id="password" name="password" type="password"
                   autocomplete="new-password"
                   <#if messagesPerField.existsError('password')>class="wac-input-error"</#if>>
            <button type="button" class="wac-pwd-toggle" data-target="password"
                    aria-label="${msg('showPassword')}">
              <i class="fa fa-eye" aria-hidden="true"></i>
            </button>
          </div>
          <#if messagesPerField.existsError('password')>
            <span class="wac-field-msg wac-field-msg--error">
              ${kcSanitize(messagesPerField.get('password'))?no_esc}
            </span>
          </#if>
        </div>

        <div class="wac-field">
          <label for="password-confirm">${msg("passwordConfirm")} <span class="wac-required">*</span></label>
          <div class="wac-password-wrap">
            <input id="password-confirm" name="password-confirm" type="password"
                   autocomplete="new-password"
                   <#if messagesPerField.existsError('password-confirm')>class="wac-input-error"</#if>>
            <button type="button" class="wac-pwd-toggle" data-target="password-confirm"
                    aria-label="${msg('showPassword')}">
              <i class="fa fa-eye" aria-hidden="true"></i>
            </button>
          </div>
          <#if messagesPerField.existsError('password-confirm')>
            <span class="wac-field-msg wac-field-msg--error">
              ${kcSanitize(messagesPerField.get('password-confirm'))?no_esc}
            </span>
          </#if>
        </div>
      </#if>

      <button type="submit" class="wac-btn-primary" style="margin-top:0.75rem">
        ${msg("doRegister")}
      </button>

    </form>

    <div class="wac-register-link">
      <a href="${url.loginUrl}">${kcSanitize(msg("backToLogin"))?no_esc}</a>
    </div>

  </div>
</div>

<div class="wac-disclaimer">
  <span>&#9888;</span>
  App dimostrativa per uso personale &mdash; non inserire dati personali reali.
  Gli account vengono eliminati automaticamente dopo 21&nbsp;giorni dalla creazione.
</div>

<script>
  document.querySelectorAll('.wac-pwd-toggle').forEach(function(btn) {
    btn.addEventListener('click', function() {
      var input = document.getElementById(btn.dataset.target);
      var icon = btn.querySelector('i');
      if (input.type === 'password') {
        input.type = 'text';
        icon.className = 'fa fa-eye-slash';
      } else {
        input.type = 'password';
        icon.className = 'fa fa-eye';
      }
    });
  });
</script>

</body>
</html>
