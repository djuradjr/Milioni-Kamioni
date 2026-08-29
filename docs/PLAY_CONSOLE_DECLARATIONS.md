# Play Console — deklaracije (copy-paste artifact)

Ovaj fajl je **jedini izvor istine** za tekst koji se lepi u Play Console.
Kad se ponašanje app-a promeni, prvo izmeni ovde, pa u Konzoli — tako je
diffabilno po release-u (najčešći razlog odbijanja a11y aplikacija je
deklaracija koja se razišla sa stvarnim ponašanjem).

Provereno prema kodu na commit-u `c7a2e03`:
- app: `com.djuki.blockbrainrot` (debug `.debug`)
- a11y klasa: `com.example.stayfree.service.StayFreeAccessibilityService`
- config: `flagReportViewIds|flagRetrieveInteractiveWindows`, `canRetrieveWindowContent=true`

---

## 1. Accessibility API — Prominent disclosure (Policy Declaration)

**Which accessibility capabilities does your app use?**
`flagReportViewIds`, `canRetrieveWindowContent` (retrieve window content /
active window info).

**Describe the functionality this enables (paste):**

> Block Brainrot is a self-control tool that the device owner configures to
> block distracting apps and websites on their own device. The Accessibility
> Service is used only to detect what is currently on screen so the block
> screen can appear at the right moment:
>
> 1. The package name of the foreground app, to know which app is open.
> 2. `viewIdResourceName` and on-screen bounds of UI nodes, to detect specific
>    short-form-video surfaces the user chose to block (e.g. Reels, Shorts).
> 3. Only when the user has added one or more websites to block: the text of
>    the browser's address bar, to read the current URL and match it against
>    the user's blocklist.
>
> The service never reads, stores, or transmits general screen text, messages,
> or content beyond the URL described above. The user selects every blocked
> target; nothing is monitored silently. All processing is on-device — the app
> has no INTERNET permission and collects, shares, or sells nothing.
>
> Before the Accessibility settings are ever opened, an in-app disclosure
> screen shows this same explanation and the user must accept it.

**Is there an alternative to the Accessibility API?** No — detecting the
foreground app/URL the instant it opens, in order to draw our own block screen,
is not possible through a less-privileged API for third-party apps.

> ⚠️ Mora se poklopiti sa in-app disclosure tekstom
> (`R.string.disclosure_message`). Ako menjaš jedno, menjaj oba.

**Attach:** screenshot in-app disclosure dijaloga + kratak screen-recording
disclosure → Accessibility settings flow.

---

## 2. Foreground Service (specialUse)

**Type:** `specialUse`. Subtype (već u manifestu):

> Continuous on-device screen-time tracking that powers the user's app-blocking
> limits and statistics. No data leaves the device.

**Why a persistent FGS is needed (paste):**

> The service tracks on-screen time continuously so per-app daily limits and
> usage statistics stay accurate even when the app is in the background. A
> persistent notification is always shown while tracking. This cannot be done
> with WorkManager/JobScheduler because enforcement must be immediate and
> continuous, not batched.

**Attach:** video koji pokazuje trajnu notifikaciju.

---

## 3. App access (instrukcije za reviewera, paste)

> No account or login exists — the app opens straight to onboarding.
> To exercise the full feature set, grant these permissions when onboarding
> asks (it walks through each): Usage access, Accessibility, Display over other
> apps, Notifications. Then add an app (e.g. Instagram) or a website to the
> block list and open it — the block screen appears. Everything is on-device;
> there is no server.

---

## 4. Data safety

- Data collected: **None.** Data shared: **None.**
- No INTERNET permission → nothing can leave the device.
- Account username/email (if set) is local-only (DataStore, backup-excluded).
- Play Billing (kad stigne) ne menja ovaj odgovor — Google procesira te podatke.

---

## 5. Store listing — kategorija i publika

- Category: **Tools** (ili Health & Fitness). **NIKAD Parenting/Families.**
- Target audience: **NOT children.** Self-blocking alat za vlasnika uređaja.
