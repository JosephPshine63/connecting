<!DOCTYPE html>
<html lang="<#if locale??>${locale.currentLanguageTag}<#else>en</#if>">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="robots" content="noindex, nofollow">
    <title>Termini e condizioni — WacChat</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap">
    <link rel="stylesheet" href="${url.resourcesPath}/css/login.css">
</head>
<body>

<div class="wac-wrap">
  <div class="wac-card wac-card--wide">

    <div class="wac-logo"><img src="${url.resourcesPath}/img/logo.png" alt="WacChat" class="wac-logo-img">WacChat</div>
    <p class="wac-page-subtitle">Termini e condizioni di utilizzo</p>

    <div class="wac-verify-body">
      <p>Prima di continuare, leggi e accetta i punti principali:</p>
      <ul style="text-align:left; margin: 0.75rem 0 1rem 1.25rem; line-height: 1.6;">
        <li>WacChat è un progetto dimostrativo/personale, non un servizio commerciale.</li>
        <li>Il servizio è fornito "così com'è", senza garanzie di disponibilità o continuità.</li>
        <li>Sei responsabile dei contenuti che invii; sono vietati contenuti illeciti, offensivi o lesivi di diritti altrui.</li>
        <li>Gli account inattivi da più di 14 giorni vengono cancellati automaticamente, insieme a chat e media associati.</li>
        <li>Il titolare può sospendere o rimuovere account che violano queste condizioni.</li>
      </ul>
      <p>
        Il testo completo di privacy e termini è disponibile qui:
        <a href="${url.resourcesPath}/privacy.html" target="_blank" rel="noopener">Privacy e termini</a>.
      </p>
    </div>

    <form class="wac-terms-actions" action="${url.loginAction}" method="POST">
      <button class="wac-btn-primary" type="submit" name="accept" id="kc-accept" value="accept">
        Accetto
      </button>
      <button class="wac-btn-primary wac-btn-outline" type="submit" name="cancel" id="kc-decline" value="cancel">
        Non accetto
      </button>
    </form>

  </div>
</div>

<div class="wac-disclaimer">
  <span>&#9888;</span>
  App dimostrativa per uso personale &mdash; non inserire dati personali reali.
  Gli account inattivi vengono eliminati automaticamente dopo 14&nbsp;giorni.
</div>

</body>
</html>
