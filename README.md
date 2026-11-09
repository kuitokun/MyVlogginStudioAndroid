# MyVloggingStudio para Android

El editor del celular como app instalable. Funciona sin internet: todo (las herramientas de video y las tipografías) viene adentro del APK, y tus videos nunca salen del teléfono.

## Cómo conseguir el APK (una sola vez, unos 10 minutos)

GitHub compila el APK por vos, gratis. No necesitás instalar nada en la compu.

1. Entrá a https://github.com y creá una cuenta si no tenés.
2. Arriba a la derecha: **+ → New repository**. Nombre: `MyVloggingStudio`. Elegí **Private** y tocá **Create repository**.
3. En la página del repositorio vacío, tocá **uploading an existing file**.
4. Descomprimí este zip en la compu y arrastrá **todo el contenido de la carpeta** (no la carpeta en sí) a la página. Tocá **Commit changes**.
5. Andá a la pestaña **Actions**. Vas a ver "Compilar APK" corriendo. Tarda unos 3–5 minutos. Cuando aparece el tilde verde, está listo.
6. En el celular, con la sesión de GitHub iniciada, entrá a tu repositorio → **Releases** (a la derecha) → **MyVloggingStudio (última versión)** → tocá **MyVloggingStudio.apk**.
7. Abrí el archivo descargado. Android te va a pedir permiso para instalar apps de Chrome: aceptalo una vez. Si Play Protect avisa que la app es desconocida, tocá **Instalar de todos modos** (es normal en apps que no están en la Play Store).

### Si la carpeta `.github` no se subió

Algunos navegadores no suben carpetas que empiezan con punto. Si en Actions no aparece nada:
1. En el repositorio: **Add file → Create new file**.
2. En el nombre escribí `.github/workflows/build.yml`.
3. Pegá el contenido del archivo `build.yml` que está en esa carpeta del zip y tocá **Commit changes**.

## Actualizaciones

Cada vez que subas archivos nuevos al repositorio, GitHub vuelve a compilar y reemplaza el APK en Releases. Como todas las versiones están firmadas con la misma clave (`keystore/release.p12`), el APK nuevo se instala encima del anterior sin perder nada. No borres ni cambies esa carpeta.

## Dónde quedan los videos

Los videos exportados se guardan en **Películas/MyVloggingStudio** y aparecen en la galería.
