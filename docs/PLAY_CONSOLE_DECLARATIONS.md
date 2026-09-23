# Play Console — deklaracije (copy-paste artifact)

Ovaj fajl je **jedini izvor istine** za tekst koji se lepi u Play Console.
Kad se ponašanje app-a promeni, prvo izmeni ovde, pa u Konzoli — tako je
diffabilno po release-u (najčešći razlog odbijanja a11y aplikacija je
deklaracija koja se razišla sa stvarnim ponašanjem).

Provereno prema kodu na grani `placanje-billing` (verzija 1.1.0, versionCode 3):
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
>
> Blocking is a paid feature. Onboarding asks for Usage access, then shows the
> subscription screen. REDEEM THE PROMO CODE BELOW in the Google Play app first
> (Play Store → profile → Payments & subscriptions → Redeem code); the app then
> unlocks and onboarding continues with the permissions blocking needs
> (Accessibility, Display over other apps, Notifications, Battery). Grant them
> when asked — onboarding walks through each.
>
> Then add an app (e.g. Instagram) or a website to the block list and open it —
> the block screen appears. Screen-time tracking and statistics work without a
> subscription. Everything is on-device; there is no server.
>
> Promo code (one-time, subscription free): <PASTE FROM Play Console →
> Monetize with Play → Promotional codes>

---

## 4. Data safety

- Data collected: **None.** Data shared: **None.**
- No INTERNET permission → nothing can leave the device.
- Account username/email (if set) is local-only (DataStore, backup-excluded).
- Play Billing ne menja ovaj odgovor — Google procesira te podatke. Provereno
  2026-09-23 na pravoj kupovini: u aplikaciji se čuva samo `premium_active`
  (boolean), bez tokena, broja porudžbine i mejla; u logovima nema ničega od toga.
- Billing biblioteka nosi Google-ov `datatransport` (telemetrija o korišćenju
  API-ja) koji bi inače tražio INTERNET. Dozvola je uklonjena iz manifesta
  (`tools:node="remove"`), pa aplikacija fizički ne može ništa da pošalje —
  provereno: nula egressa iz našeg procesa. Posle svake nadogradnje Billing-a
  proveri `aapt2 dump permissions` na release APK-u.

---

## 5. Store listing — kategorija i publika

- Category: **Tools** (ili Health & Fitness). **NIKAD Parenting/Families.**
- Target audience: **NOT children.** Self-blocking alat za vlasnika uređaja.
- Listing mora da kaže da je blokiranje deo pretplate (merenje vremena i
  statistika ostaju besplatni) — opis koji to prećuti je obmanjujuć.
- Screenshot-ovi moraju da prikažu i ekran sa pretplatom.

---

## 6. Pretplata (Play Billing)

- Proizvod: `premium`, bazni planovi `monthly` / `quarterly` / `semiannual`,
  ponude `trial-monthly` / `trial-quarterly` / `trial-semiannual` (7 dana,
  samo za one koji pretplatu nikad nisu imali).
- Paywall pre kupovine prikazuje: cenu iz Play-a, period, dužinu probe, kada
  počinje naplata i da se otkazuje u Google Play-u. Bez lažne hitnosti; mesečni
  plan je unapred izabran, nijedan skuplji nije.
- Otkazivanje ide kroz Play (Podešavanja → Premium → upravljanje pretplatom).
  U aplikaciji nema zasebnog toka otkazivanja.
- Kad pretplata istekne ili bude povučena, blokade se GASE i korisnik ih uvek
  može isključiti bez plaćanja — aplikacija nikad ne drži uređaj zaključan.
