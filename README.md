# 📚 Estante — Leitor de Livros PDF (Android + Jetpack Compose)

Aplicativo Android nativo para ler livros em **PDF**, com biblioteca pessoal,
progresso de leitura, marcadores, modos de leitura e download gratuito de
livros clássicos pelo catálogo do **Project Gutenberg**.

> ✅ Projeto verificado: compilado com sucesso (Kotlin 2.0.20, AGP 8.5.2,
> compileSdk 34) e APK debug gerado durante o desenvolvimento.

---

## Funcionalidades

| Área | O que faz |
|---|---|
| **Biblioteca** | Estante em grade com capas (geradas da 1ª página do PDF), autor, % lido e card "Continuar lendo" com o último livro aberto |
| **Importar PDF** | Botão "Importar PDF" abre o seletor de arquivos — sem permissões de armazenamento (o arquivo é copiado para o armazenamento interno do app) |
| **Explorar Gutenberg** | Busca por título/autor no catálogo Gutendex (milhares de livros em domínio público), com capa, progresso de download e botão "Abrir" ao concluir |
| **Leitor PDF** | Página a página com gestos: deslize para virar, pinça para ampliar (até 6×), toque nas laterais para virar página, toque no centro para mostrar/ocultar controles |
| **Progresso** | Página atual salva automaticamente — ao reabrir, continua de onde parou |
| **Marcadores** | Marque qualquer página; liste, acesse e remova marcadores |
| **Modos de leitura** | Claro, Sépia e Noturno (inversão de cores para ler no escuro) |
| **Brilho** | Controle de brilho dentro do leitor, sem sair do livro |
| **Navegação** | Slider para saltar para qualquer página |
| **Tema do app** | Sistema / Claro / Escuro (cor dinâmica no Android 12+) |
| **Edição/Exclusão** | Toque e segure num livro para editar título/autor ou excluir (arquivos incluídos) |

> **Nota sobre PDF:** PDF é um formato de layout fixo, então não há "troca de
> fonte/tamanho" como em EPUBs — o app oferece **zoom** e **modos de cor**
> no lugar. PDFs protegidos por senha não são suportados (limitação do
> `PdfRenderer` nativo do Android).

---

## Requisitos

- **Android Studio** Koala (2024.1.1) ou mais novo — já vem com o JDK 17 e o Android SDK
- Conexão com a internet no primeiro build (o Gradle baixa as dependências)
- Celular/emulador com **Android 8.0 (API 26)** ou mais novo

## Como abrir e rodar (passo a passo)

1. **Extraia** o zip do projeto (se ainda não o fez) para uma pasta, ex.: `~/Projetos/Estante`.
2. Abra o **Android Studio** → **File ▸ Open…** → selecione a pasta `Estante` (a que contém `settings.gradle.kts`).
3. Se aparecer a pergunta *"Trust project?"*, clique em **Trust**.
4. O Android Studio fará o **Gradle Sync** automaticamente e baixará as dependências na primeira vez (pode levar alguns minutos). Se ele pedir para instalar/atualizar o SDK ou as ferramentas, aceite.
5. Conecte o celular com **Depuração USB** ativada (ou inicie um emulador).
6. Clique em **Run ▸ Run 'app'** (ou no ▶️ verde).

Pronto — o app instala e abre na sua estante.

### Gerando o APK manualmente (opcional)

- APK de teste (debug): `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- APK assinado para distribuição: **Build ▸ Generate Signed Bundle / APK…** no Android Studio

---

## Estrutura do projeto

```
Estante/
├── settings.gradle.kts            # Módulos e repositórios
├── build.gradle.kts               # Plugins do projeto (raiz)
├── gradle/libs.versions.toml      # Catálogo de versões (todas as libs)
├── gradle/wrapper/                # Gradle Wrapper (8.9)
└── app/
    ├── build.gradle.kts           # Configuração do módulo app
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/                   # Ícone adaptativo, temas, strings
        └── java/com/example/estante/
            ├── EstanteApp.kt          # Application + injeção simples (singleton)
            ├── MainActivity.kt        # Activity única + navegação (NavHost)
            ├── data/
            │   ├── db/                # Room: entidades, DAOs e banco
            │   ├── gutenberg/         # API Gutendex + downloader com progresso
            │   ├── BookRepository.kt  # Estante + arquivos (importar/download)
            │   └── SettingsRepository.kt # DataStore: tema e modo de leitura
            ├── pdf/
            │   ├── PdfBook.kt         # PdfRenderer com cache LRU de páginas
            │   └── PdfUtils.kt        # Contagem de páginas + capa da 1ª página
            └── ui/
                ├── theme/             # Material 3 (claro/escuro/dinâmico)
                ├── components/        # BookCover (capa com placeholder)
                ├── library/           # Estante (grade, importar, editar, excluir)
                ├── browser/           # Catálogo Gutenberg (busca e download)
                └── reader/            # Leitor (pager, zoom, marcadores, ajustes)
```

## Tecnologias

- **Kotlin** + **Jetpack Compose** (BOM 2024.09, Material 3)
- **PdfRenderer** (API nativa do Android) — sem bibliotecas nativas extras
- **Room** + **KSP** — livros e marcadores
- **DataStore** — preferências (tema, modo de leitura)
- **Navigation Compose**, **ViewModel** + **Flow**/coroutines
- **Retrofit + kotlinx.serialization** — catálogo Gutendex
- **OkHttp** — download dos PDFs com progresso
- **Coil** — capas dos livros
- **AGP 8.5.2 / Gradle 8.9 / compileSdk 34 / minSdk 26**

## Onde ficam os arquivos dos livros?

No armazenamento **interno e privado** do app
(`Android/data/com.example.estante/files/books/` e `.../covers/`).
Excluir o livro pela interface remove também os arquivos. Desinstalar o app
apaga tudo — os PDFs originais que você importou continuam onde estavam.

## Dicas de uso do leitor

- 📖 Toque na **esquerda/direita** da tela → página anterior/próxima
- 🖲️ Toque no **centro** → mostra/oculta barras e controles
- 🔍 **Dois dedos** → ampliar/arrastar (o swipe vira página novamente ao voltar ao zoom 1×)
- 📑 Ícone de **marcador** (barra superior) → salva a página atual
- 🎨 Ícone de **engrenagem** → modo Claro/Sépia/Noturno e brilho

## Problemas comuns

| Situação | Solução |
|---|---|
| "SDK location not found" | Deixe o Android Studio criar o `local.properties` (não o incluí porque ele contém o caminho da máquina de quem compilou) |
| Erro de versão do JDK | Use o JDK embutido do Android Studio (Settings ▸ Build Tools ▸ Gradle ▸ Gradle JDK = 17) |
| Downloads do Gutenberg falham | Verifique a internet; alguns livros muito antigos não têm versão em PDF (a busca já filtra só os que têm) |
| O app abre mas não lista livros | Normal no primeiro uso — importe um PDF ou baixe um livro do Gutenberg |

## Personalizando

- **Nome do app:** `app/src/main/res/values/strings.xml` → `app_name`
- **Cores do tema:** `ui/theme/Theme.kt`
- **Ícone:** `res/drawable/ic_launcher_foreground.xml` + `res/values/colors.xml`
- **Id do aplicativo (para publicar):** `applicationId` em `app/build.gradle.kts` (e refatore o pacote no Android Studio com *Rename* se quiser)
