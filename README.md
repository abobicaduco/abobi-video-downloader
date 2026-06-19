# Abobi Video Downloader

[![Android](https://img.shields.io/badge/Android-8.0%2B-green?logo=android)](https://developer.android.com/)
[![License: GPL-3.0](https://img.shields.io/badge/Licença-GPL--3.0-blue.svg)](LICENSE)

> Baixador simples de vídeos e áudio para Android — YouTube, Instagram, TikTok e mais de 1000 sites.

---

## Funcionalidades

- **Download simples** — cole o link, escolha vídeo ou áudio e baixe
- **Qualidade automática** — melhor qualidade disponível, até 2160p 60fps
- Suporte a **1000+ sites** via yt-dlp (YouTube, Instagram, TikTok, Twitter/X, Facebook…)
- **Cookies** para conteúdo que exige login
- Histórico de downloads
- Interface em **Português, English** e mais de 20 idiomas
- Tema Material You

---

## Download

APK arm64 (release): baixe o artefato **`release-apks`** no workflow [Build Release APK](https://github.com/abobicaduco/abobi-video-downloader/actions/workflows/android.yml) (branch `caduco-stable` → *Run workflow* ou último run concluído → *Artifacts*).

Dentro do zip: `Abobi-Video-Downloader-*-release.apk`.

---

## Como compilar

**Pré-requisitos:** Android Studio · SDK API 26+ · JDK 17 · Kotlin

```bash
git clone https://github.com/abobicaduco/abobi-video-downloader.git
cd abobi-video-downloader
./gradlew assembleRelease -PnoSplits
```

Debug local:

```bash
./gradlew assembleDebug --no-daemon
```

---

## Outros Apps AboBI

| App | Descrição |
|---|---|
| [AboBI Player](https://github.com/abobicaduco/abobiplayer) | Player de vídeo local para Android |
| [AboBI Reação](https://github.com/abobicaduco/abobireacao) | Grave sua reação ao vivo enquanto assiste a vídeos |
| [AboBI Sonora](https://github.com/abobicaduco/abobi-sonora) | Player de música offline para Android |
| [AboBI Ferramentas](https://abobiferramentas.com) | Catálogo de APKs MOD e FOSS para Android |

---

## Apoiar

Se o Abobi Video Downloader te ajudou, considera um Pix de qualquer valor!

**Chave Pix (aleatória):** `f74458dc-2a36-49bd-9250-1cef4365ebb8`

Site: [abobiferramentas.com](https://abobiferramentas.com)

Repositório: [github.com/abobicaduco/abobi-video-downloader](https://github.com/abobicaduco/abobi-video-downloader)

---

**Mantido por** [Carlos Eduardo (@abobicaduco)](https://github.com/abobicaduco)
