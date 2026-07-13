const hash = location.hash.startsWith("#") ? location.hash.slice(1) : "";
const parameters = new URLSearchParams(hash);
const authType = parameters.get("type");
const accessToken = parameters.get("access_token");
const isPatientCallback = Boolean(accessToken) && ["invite", "recovery", "signup", "magiclink"].includes(authType);

if (isPatientCallback) {
  const appUrl = `neurovibe://auth/callback#${hash}`;
  document.title = "Continue in NeuroVibe";
  document.body.innerHTML = `
    <main class="login-panel" style="max-width:560px;margin:auto">
      <div class="login-brand"><span class="brand-mark"><span class="material-symbols-outlined">neurology</span></span><div><div class="brand-title">NeuroVibe</div><div class="eyebrow">Patient account</div></div></div>
      <section class="login-card" aria-labelledby="callback-title">
        <h1 id="callback-title">Continue in the NeuroVibe app</h1>
        <p class="muted">Your invitation has been verified. Open the installed app to create or update your private password.</p>
        <a class="btn btn-primary" id="open-patient-app" style="width:100%;justify-content:center" href="#">Open NeuroVibe <span class="material-symbols-outlined">open_in_new</span></a>
        <p class="muted" style="margin-top:18px;font-size:.9rem">If the app does not open, install the latest NeuroVibe Android app and tap the button again.</p>
      </section>
    </main>`;
  document.querySelector("#open-patient-app").href = appUrl;
} else {
  await import("./app.js");
}
