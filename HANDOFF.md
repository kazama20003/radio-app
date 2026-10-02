# HANDOFF — Radio MAPE (contexto para continuar en otra sesión)

_Última actualización: 2026-10-02_

## 1. Qué es el proyecto
Radio push-to-talk (walkie-talkie) para flota, con mapa/tracking, chat y alertas.
- **App nativa Android** (Kotlin + Jetpack Compose) — reescritura de la app RN vieja.
- **Backend NestJS** con **mediasoup SFU** (audio en vivo) desplegado en un VPS.

## 2. Repos / rutas / ramas
| Componente | Repo GitHub | Rama | Ruta local |
|---|---|---|---|
| App Android | `github.com/kazama20003/radio-app` | `main` | `C:\kazama-works\zzzz\radio-mape\radio-app` |
| Backend | `github.com/kazama20003/radio-backend` | `main` | `C:\kazama-works\zzzz\radio-mape\mape-app-backend` |

**Siempre trabajar/commitear en `main`** (ambos repos). Último estado:
- App: `d500e5e`  ·  Backend: `c6c13ec`

Paquete Android: `com.syemape.radio`  ·  API: `https://radio.syemape.com/api`

## 3. Compilar la APP (requiere JDK 21)
El `JAVA_HOME` del sistema es 17; usar el **JBR de Android Studio (JDK 21)**:
```bash
cd C:/kazama-works/zzzz/radio-mape/radio-app
# Debug (para probar rápido):
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug
# Release FIRMADO (para distribuir):
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleRelease
```
**APKs resultantes:**
- Release firmado: `radio-app/app/build/outputs/apk/release/app-release.apk` (~51 MB)
- Debug: `radio-app/app/build/outputs/apk/debug/app-debug.apk` (~59 MB)

**Instalar en emulador/dispositivo:**
```bash
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" install -r <ruta-apk>
# (release y debug tienen firmas distintas; para cambiar de uno a otro: adb uninstall com.syemape.radio primero)
```

### Firma del release (GUARDAR — irremplazable)
Si se pierde el keystore o la contraseña, NO se puede actualizar la app publicada.
- Keystore: `radio-app/keystore/release.jks`  (gitignored, NO está en GitHub — respaldar aparte)
- Credenciales: `radio-app/keystore.properties`  (gitignored)
- Password (store y key): `RadioMape2026Key`  ·  Alias: `radio-mape`
- SHA-1: `DB:0F:30:8A:15:6A:DF:BE:09:F0:AB:51:26:73:A2:5F:38:5B:38:92`
- Config en `app/build.gradle.kts` (`signingConfigs.release` lee `keystore.properties`).
- Maps API key en el manifest, sin restringir (funciona con esta firma).

## 4. Desplegar el BACKEND (en el VPS)
VPS: `root@sv-gPLJxt5WY8jTzOINaHBf` (IP pública **161.132.49.197**, **sin NAT**), ffmpeg 4.2.7 instalado.
Ruta: `/var/www/syemape/radio-backend`  ·  pm2 id **3** (`radio-backend`, puerto 3050).
```bash
cd /var/www/syemape/radio-backend && git pull && node_modules/.bin/nest build && pm2 restart radio-backend --update-env
pm2 logs radio-backend --lines 30 --nostream   # ver logs
pm2 list                                        # ver estado y reinicios (↺)
```
**`.env` del backend (ya configurado):**
```
MEDIASOUP_ANNOUNCED_IP=161.132.49.197
MEDIASOUP_RTC_MIN_PORT=40000
MEDIASOUP_RTC_MAX_PORT=40999      # ampliado de 40100 (era el cuello de botella)
```
Firewall: `ufw` está **inactivo** (no bloquea). `/uploads` se sirve estático.

## 5. Cuenta de prueba
DNI **40420485** / contraseña **40420485** (operador "Comando 1"/"Halcón").
Canales: **Canal 1** (`cmuineetg0004w2y4lhrrwhb8`) y **Ruta** (`cmuldj8jz003tw2y428dt0u7g`).

## 6. Arquitectura de la radio (importante)
- Audio en vivo por **mediasoup SFU** (NO LiveKit — se quitó; NO WebRTC P2P). Señalización por Socket.IO `/radio` (`ms:*`, `channel:join/leave`, `channel:presence`).
- **Notas de voz:** el backend **graba el PTT con ffmpeg** (`startRecording` en `ms:produce` → `finishRecording` en `ms:closeProducer` → emite `ptt:ended`). Se guardan como **ADTS `.aac`** en `/uploads` y aparecen en el **chat del canal** (`ChannelChatScreen`). El cliente solo transmite; NO sube el audio.

## 7. Lo resuelto en la sesión del 2026-10-02 (commits)
**App:**
- Fix "No se puede transmitir": setup mediasoup reintentable + bajo demanda; fix reconexión (rearmar transports); fix produce usa canal ACTUAL (no capturado).
- Audio del sistema: solo se toma foco+modo llamada MIENTRAS hay voz (no bloquea otras apps en reposo); foco `GAIN_TRANSIENT`.
- Volumen de radio funcional (slider real sobre `STREAM_VOICE_CALL`, persistido).
- Notificaciones push de alertas (canales + anti-duck con la radio activa) + íconos propios; notif de radio rediseñada; foreground service robusto (microphone→mediaPlayback fallback, no crashea).
- AlertsScreen rediseñada (estado vacío + tarjetas con icono por tipo).
- Presencia: muestra QUIÉN está conectado (nombres) + fix "null" + fix "4 en todos los canales" + me muestro al instante (sin "0 conectados").
- Salida de audio (Bluetooth/Auricular/Teléfono) detectada al conectar.
- Voz: AGC reactivado (voz fuerte) + supresión de ruido.

**Backend:**
- Notas de voz arregladas (ffmpeg): handler `'error'` (no crashea), ADTS `.aac`, guardar on-close con `-flush_packets 1`, guardar solo si hay audio real, logs de diagnóstico. Red de seguridad global `uncaughtException`.
- Presencia emite lista de usuarios (`channel:presence` con `users[]`).
- Quitado LiveKit por completo.
- **Worker mediasoup resiliente**: al morir el worker lo recrea (no `process.exit`) y fuerza reconexión de clientes; `process.exit` solo de último recurso.
- Puertos RTC ampliados a 40999 (en `.env`).

## 8. Pendiente / verificar / ideas
- **Verificar en dispositivo real:** que la voz se oiga fuerte y natural (AGC on); que las notas de voz aparezcan y suenen; que la presencia muestre nombres correctos y el conteo por canal sea correcto; que al cambiar de canal no haya lag.
- **Vigilar estabilidad:** `pm2 list` → el contador `↺` de `radio-backend` debe DEJAR de subir solo (estaba en ~38 por los reinicios viejos).
- **Error 416 `RangeNotSatisfiable`** al servir `/uploads` (afecta reproducción en algunos casos; era por archivos viejos de 0 bytes — con los `.aac` nuevos no debería pasar).
- **Desfase inicio de grabación / beeps:** los beeps start/stop ya existen; si se corta la primera palabra, habría que retrasar la captura ~0.5s (pendiente de decidir).
- **Multi-worker:** NO implementado a propósito (innecesario para audio, refactor grande/riesgoso). Solo si crecen a cientos de canales simultáneos.
- **Notas de voz en chat 1-a-1 (no de canal):** `message:new` es por-sala; no hay push global sin FCM.

## 9. Emulador (notas)
- AVD `Pixel_9a` (`emulator-5554`). `adb` en `$LOCALAPPDATA/Android/Sdk/platform-tools/`.
- El micro del emulador graba por WebRTC (el SIGABRT que aparece es del HAL de audio del emulador, NO de la app). El audio real se valida mejor en dispositivo físico.
- La barra inferior cambia de posición según la pestaña activa (ojo con las coordenadas de taps en pruebas).
