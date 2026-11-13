# Philips 43PUS7354: il mio tema

Philips 43PUS7354 (TP Vision TPM191E, Android TV 12, 2 GB di RAM). L'elenco delle app da disattivare per questa
famiglia di TV è in [`examples/philips-tpm191e`](../philips-tpm191e/debloat.txt); qui c'è solo il tema che uso,
con il pannello meteo (`weather_panel`) di questo fork.

- [`config.json`](config.json): dock con Kodi, RaiPlay, YouTube, Prime Video e Disney+ (più Infinity, che compare solo se installata); una riga di app
  sotto il dock; statistiche sotto l'orologio; pressione lunga di Home sul telecomando originale
  (`home_long_press_home`); orologio a tutto schermo dopo 3 minuti di inattività; meteo sotto l'orologio.
  `greeting_name` è il nome nel saluto in alto: va cambiato con il proprio.

## Comandi usati

Al posto di `192.168.1.50` va l'indirizzo della propria TV.

```bash
adb connect 192.168.1.50:5555
adb -s 192.168.1.50:5555 shell pm list packages -3          # app installate, prima di cambiare qualcosa

tools/docker-build.sh addons/weather
tools/docker-build.sh launcher testReleaseUnitTest lintRelease assembleRelease
adb -s 192.168.1.50:5555 install -r addons/weather/app/build/outputs/apk/release/app-release.apk
SKIP_BUILD=1 HOME_KEY_SERVICE=1 TV_HOST=192.168.1.50 launcher/build-and-install.sh

TV_HOST=192.168.1.50 tools/theme.sh examples/philips-43pus7354   # pubblica questo tema
adb -s 192.168.1.50:5555 exec-out screencap -p > shot.png         # screenshot per controllare
```

Per tornare indietro: `TV_HOST=192.168.1.50 tools/theme.sh --reset` toglie il tema.
