# BGG Play Logger

An Android app that logs your board game plays on BoardGameGeek from a photo.

1. **Take a photo** of the game on the table.
2. **Claude identifies the game**, finds it on BGG, and guesses the players (names or colours) and any scores it can read.
3. **Check** players, scores, winner, date, location and length.
4. **Log play on BGG** with one tap.

**My games** (top-right) lists every game you've played, filterable by **theme** (BGG categories such as Animals, Fantasy, Economic…), with cover pictures. Tap a game for its details: cover, players, play time, rating, weight, themes, mechanics, designers, description, your play dates, and a button to open it on BGG.

Interface in English or French, depending on the phone's language.

---

## Get the APK (no coding needed, about 10 minutes the first time)

GitHub compiles the app for free.

1. Create a free account at **github.com** (skip if you have one).
2. Tap **+ → New repository**. Name it `bgg-play-logger`, leave everything else as is, tap **Create repository**.
3. On the new repository page, tap **uploading an existing file**.
4. Unzip `BGGPlayLogger.zip` on a computer and drag **everything inside the `BGGPlayLogger` folder** (including the hidden `.github` folder) onto the page, then tap **Commit changes**.
   - On a Mac, press **Cmd + Shift + .** in Finder to show the hidden `.github` folder. On Windows, turn on *View → Hidden items*.
   - If `.github` won't upload: tap **Add file → Create new file**, type the name `.github/workflows/build.yml`, paste the contents of that file, and commit.
5. Open the **Actions** tab. A build named *Build APK* starts by itself and takes about 5 minutes (green check when done).
6. Open **Releases** (right side of the repository's main page) on your phone and tap **BGG-Play-Logger.apk**.
7. Android asks to allow installing apps from your browser: allow it, then install.

Every time you change a file in the repository, a new APK is built automatically.

## First launch: settings

| Setting | Where to get it |
|---|---|
| **Anthropic API key** | console.anthropic.com → API keys. About a cent per photo. |
| **Model** | Leave the default. |
| **BGG username / password** | Your BoardGameGeek login, used to log plays. |
| **BGG app token** | Needed for *My games* (history, covers, themes). On BGG, register an application at boardgamegeek.com/applications (free for personal use) and paste its token. |

Everything is stored only on the phone.

## Good to know

- **Logging plays** uses the same sign-in and play form as the BGG website, because BGG has no official API for logging plays. If BGG changes its website, this part may need an update.
- **Scores** are only filled when Claude can actually read them (score track, score pad). Always check before logging.
- **My games without a token**: plays you log with the app still appear, and covers and themes are fetched from the BGG website when possible, but your earlier history needs the token.
- The app is a "debug" build signed with a test key. That's normal for an app you install yourself; it isn't on the Play Store.
