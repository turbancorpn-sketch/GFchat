# GFchat

Do logo (aap aur aapki girlfriend) ke liye private Android chat app.
Kotlin + Jetpack Compose + Material 3, backend Supabase.

## Abhi kya bana hai

**Stage 1 (phone lock + UI)**
- 4-digit secret code (hashed, 5 galat try ke baad 30 second lockout) aur fingerprint unlock.
- App 15 second se zyada background me rahe to dobara lock (`RELOCK_AFTER_MS` in `MainActivity.kt`). Screenshot band (`FLAG_SECURE`).
- Chat UI (text, gallery se photo/video).

**Stage 2A (login + pairing)**
- Email + password login/sign-up (Supabase Auth). Session encrypted storage me save hota hai, to login sirf ek baar.
- Pairing: ek phone 8-character code banata hai (15 minute valid, ek hi baar chalta hai), doosra phone wo code daalta hai. Judne ke baad teesra koi nahi jud sakta (database level pe enforce hota hai).
- **Force update**: app khulte hi (login se pehle) Supabase ke `app_config` table se minimum version check karta hai. Phone ka version usse kam ho to "Update karo" screen dikhti hai, aage app use nahi hoti. Naya version force karna ho: Supabase Table Editor me `app_config` row ka `min_version_code` badhao (GitHub Actions ka har build number apne aap version code ban jata hai, Actions tab me "Build APK" run ka number dekh lo).
- **Admin panel**: jisne sabse pehle pairing code banaya tha (wahi "admin"), usi phone pe chat screen ke top bar me gear icon dikhta hai. Isse force-update ka minimum version seedha app se set kar sakte ho, Supabase dashboard me jaane ki zarurat nahi. Partner ke phone pe ye icon nahi aata (database level pe check hota hai, sirf UI nahi).
- **Theme**: chat screen ke top bar me ek icon se Light / Dark / System (phone ki setting ke hisaab se) switch kar sakte ho. Har phone apni setting alag rakhta hai.
- **App icon**: simple laal gol icon (adaptive icon, `app/src/main/res/drawable/ic_launcher_foreground.xml` me badal sakte ho).

**Stage 2B (real text messaging)**
- Text messages ab asli hain — Supabase `messages` table me save hote hain, sirf tumhare couple ke 2 accounts hi padh/likh sakte hain (RLS se enforce).
- Supabase Realtime (websocket) se naye message/ticks turant push hote hain (`RealtimeClient.kt`); agar socket kabhi na jud paaye to har 2.5 second ka safety-net poll backup rehta hai (`ChatViewModel.kt`) — isliye kabhi bhi update miss nahi hoga.
- Naya message aane par notification tone (phone ki default notification sound) + halki vibration bajti hai — lekin sirf jab tak Chat screen khula ho.
- **Ticks**: ek tick (sent), do gray tick (delivered — partner ke phone tak pahuncha), do blue tick (read — partner ne chat kholi).

**Stage 2C (photo/video upload)**
- Photo/video ab Supabase ke private `media` storage bucket me upload hote hain, sirf tumhare couple ke 2 accounts hi dekh sakte hain (RLS se enforce, `supabase/005_storage.sql`).
- Dikhane ke liye time-limited (1 ghanta) signed URL banta hai — file khud private rehti hai.
- Photo seedha chat me dikhta hai; video pe tap karne se phone ke browser/player me khulta hai.

**Stage 2D (delete + faster sync)**
- Har 1.2 second me sync (pehle 3 second tha).
- **Delete for everyone**: apna koi bhi message (text/photo/video) long-press karke delete kar sakte ho — dono phones se hat jaata hai, jagah pe "Ye message delete ho chuka hai" dikhta hai. Partner ka message tum delete nahi kar sakte.
- **Chat clear karo**: top bar ke ⋮ menu se poori chat dono phones se ek saath clear ho jaati hai (koi confirm wapas nahi la sakta).
- Bug fix: pehle ticks (delivered/read) sirf naye messages ke liye update hote the, purane message ki tick badalne pe sender ko pata nahi chalta tha. Ab sab sync hota hai.

## Abhi kya NAHI chalta (sach me)

- **Audio/video call hata diya gaya hai** — pehle sirf UI demo tha (asli awaaz/video nahi jaata tha), isliye button hi hata diya taaki galatfehmi na ho. Real calling (WebRTC + signaling/TURN server) chahiye to Stage 3 me alag se banega.
- Push notification nahi hai — message tabhi milega jab Chat screen khuli ho (poll-based + in-app tone). App band ho ya kisi aur screen pe ho to turant pata nahi chalega.
- Stage 2A/2B/2C/2D ka code Android Studio me compile ya asli Supabase ke saath test nahi kiya gaya. Pehli baar chalane pe chhoti errors aa sakti hain.

## Setup (ek baar)

1. [supabase.com](https://supabase.com) pe free project banao.
2. Dashboard -> **SQL Editor** me `supabase/` folder ki saari files **number ke order me** (001 se 006 tak), ek-ek karke poora content paste karke Run karo.
3. Dashboard -> **Authentication -> Sign In / Providers -> Email**: "Confirm email" band kar do (warna sign-up ke baad email link kholna padega; app dono sambhalti hai).
4. `secrets.properties.example` ko `secrets.properties` naam se copy karo aur **Project Settings -> API** se `SUPABASE_URL` aur `SUPABASE_ANON_KEY` (anon/public key) daalo. Ye file git me nahi jaati. `service_role` key kabhi app me mat daalna.
5. Android Studio me folder open karo, Gradle sync, phir phone/emulator pe Run (minSdk 26).
6. **Dono accounts ban jaane ke baad**: Authentication -> Sign In / Providers me "Allow new users to sign up" band kar do. Isse koi aur naya account ban hi nahi sakta.

## Test kaise karein

1. Phone 1: secret code banao -> email+password se account banao -> "Mera pairing code banao".
2. Phone 2: secret code banao -> alag email se account banao -> wo code daalo -> "Judo".
3. Phone 1 par 5 second ke andar khud chat khul jani chahiye.
4. Teesra account banao (sign-up band karne se pehle) aur code try karo: "Code galat hai" aana chahiye.

## Aage

- **Stage 2E** – push notification (FCM) taaki background me bhi message mil jaye.
- **Screen-share alert** – app khula ho aur uski screen record/share ho rahi ho to dono phones pe warning dikhe aur partner ko red alert notification jaye. Limit: sirf app ke andar detect hota hai, poore phone ki screen share nahi pakad sakta.
- **Stage 3** – real audio + video call (WebRTC, signaling + TURN) — call button fir se add hoga jab yeh ready ho.
- **Stage 4** – end-to-end encryption, voice messages, read receipts, Hindi polish.
