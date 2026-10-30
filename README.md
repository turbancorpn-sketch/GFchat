# GFchat

Do logo (aap aur aapki girlfriend) ke liye private Android chat app.
Kotlin + Jetpack Compose + Material 3, backend Supabase.

## Abhi kya bana hai

**Stage 1 (phone lock + UI)**
- 4-digit secret code (hashed, 5 galat try ke baad 30 second lockout) aur fingerprint unlock.
- App 15 second se zyada background me rahe to dobara lock (`RELOCK_AFTER_MS` in `MainActivity.kt`). Screenshot band (`FLAG_SECURE`).
- Chat UI (text, gallery se photo/video), audio call aur video call screens.

**Stage 2A (login + pairing)**
- Email + password login/sign-up (Supabase Auth). Session encrypted storage me save hota hai, to login sirf ek baar.
- Pairing: ek phone 8-character code banata hai (15 minute valid, ek hi baar chalta hai), doosra phone wo code daalta hai. Judne ke baad teesra koi nahi jud sakta (database level pe enforce hota hai).
- **Force update**: app khulte hi (login se pehle) Supabase ke `app_config` table se minimum version check karta hai. Phone ka version usse kam ho to "Update karo" screen dikhti hai, aage app use nahi hoti. Naya version force karna ho: Supabase Table Editor me `app_config` row ka `min_version_code` badhao (GitHub Actions ka har build number apne aap version code ban jata hai, Actions tab me "Build APK" run ka number dekh lo).
- **Admin panel**: jisne sabse pehle pairing code banaya tha (wahi "admin"), usi phone pe chat screen ke top bar me gear icon dikhta hai. Isse force-update ka minimum version seedha app se set kar sakte ho, Supabase dashboard me jaane ki zarurat nahi. Partner ke phone pe ye icon nahi aata (database level pe check hota hai, sirf UI nahi).
- **Theme**: chat screen ke top bar me ek icon se Light / Dark / System (phone ki setting ke hisaab se) switch kar sakte ho. Har phone apni setting alag rakhta hai.
- **App icon**: simple laal gol icon (adaptive icon, `app/src/main/res/drawable/ic_launcher_foreground.xml` me badal sakte ho).

**Stage 2B (real text messaging)**
- Text messages ab asli hain — Supabase `messages` table me save hote hain, sirf tumhare couple ke 2 accounts hi padh/likh sakte hain (RLS se enforce).
- Har 3 second me naye messages check hote hain (`ChatViewModel.kt`, poll-based — asli push notification Stage 3 me).
- **Ticks**: ek tick (sent), do gray tick (delivered — partner ke phone tak pahuncha), do blue tick (read — partner ne chat kholi).

## Abhi kya NAHI chalta (sach me)

- Photo/video abhi bhi sirf tumhare apne phone pe dikhte hain, partner ko nahi jaate (upload Stage 2C me).
- Calls me awaaz aur video nahi jaati, sirf UI hai (Stage 3).
- Push notification nahi hai — message tabhi dikhega jab app khuli ho (poll-based). App band/background me ho to turant pata nahi chalega.
- Stage 2A/2B ka code Android Studio me compile ya asli Supabase ke saath test nahi kiya gaya. Pehli baar chalane pe chhoti errors aa sakti hain.

## Setup (ek baar)

1. [supabase.com](https://supabase.com) pe free project banao.
2. Dashboard -> **SQL Editor** me `supabase/001_pairing.sql`, `supabase/002_app_config.sql`, `supabase/003_admin.sql`, `supabase/004_messages.sql` — chaaron ka poora content (alag-alag, isi order me) paste karke Run karo.
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

- **Stage 2C** – photo/video upload (private Storage), push notification (FCM) taaki background me bhi message mil jaye.
- **Screen-share alert (Stage 2B)** – GFchat khula ho aur uski screen record/share ho rahi ho to dono phones pe warning dikhe aur partner ko red alert notification jaye. Dono ke liye same rule, aur jis phone pe detect hota hai wahan bhi bataya jata hai. Limit: sirf GFchat ke andar detect hota hai (Android 15+ pe screen-recording callback, Android 14 pe screenshot callback), poore phone ki screen share nahi pakad sakta.
- **Stage 3** – real audio + video call (WebRTC, signaling + TURN).
- **Stage 4** – end-to-end encryption, voice messages, read receipts, Hindi polish.
