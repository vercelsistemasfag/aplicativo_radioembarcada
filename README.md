# Rádio Embarcada — catálogo remoto R2

Rádio Alce reproduz faixas individuais da programação, sem emissora externa. **RemoteMusicProvider é a fonte ativa** em debug e release. O APK padrão não contém MP3 e não usa `MUSIC_ARCHIVE_URL`, credenciais R2, Jamendo ou URLs assinadas.

Catálogo público do MVP: [music_catalog.json](https://pub-e38948fd737d4d969bb4e65eebed0249.r2.dev/music_catalog.json). O catálogo atual possui versão 1 e 77 itens MUSIC; esses números não são fixados no código. A configuração fica em `network/catalog/RemoteCatalogConfiguration.kt`.

## Abrir e executar

- Android Studio Meerkat 2024.3.1 ou superior, compatível com AGP 8.9.2.
- JDK 17, SDK Platform 35, Build Tools 35.0.0; Gradle Wrapper 8.11.1.
- Android 8.0/API 26 ou superior. Internet necessária para a primeira obtenção do catálogo e áudio.

Abra a raiz no Android Studio, sincronize e execute `app` em debug. Configure `JAVA_HOME`/`ANDROID_HOME` ou `sdk.dir` no `local.properties` ignorado. No Windows, use `gradlew.bat`.

```sh
./gradlew test testDebugUnitTest lintDebug assembleDebug --warning-mode all --max-workers=2
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

A tela mantém Rádio Alce, empresa, AO VIVO, faixa atual, artista quando preenchido, Play/Pause e estado. O tenant continua em `app/src/main/assets/tenant.json`, separado do catálogo. Nenhum backend, autenticação, escolha de gênero, publicidade ou interface de gerenciamento foi acrescentado.

## Arquitetura

```text
RemoteMusicProvider → AutomaticProgramming / QueueBuilder → ProgramItem → ProgramPlayer → MediaSession → UI
```

| Parte | Responsabilidade |
| --- | --- |
| `network/catalog` | Configuração, HTTP limitado e parsing do JSON público para modelos internos. |
| `data/music/MusicProvider` | Contrato do catálogo, incluindo catálogo completo, revisão e suporte a versão salva. |
| `storage/FileMusicCatalog` | Último JSON válido, salvo atomicamente no `filesDir` privado. |
| `model` | Track, ProgramItem, NowPlaying e estados; não dependem do JSON externo. |
| `programming` | Ciclos pseudoaleatórios completos, lotes de 20 e reposição com até três próximas faixas. |
| `player/ProgramPlayer` | Uma instância ExoPlayer, fontes progressivas, preload, buffer, cache e metadados. |
| `player/RadioService` | MediaLibraryService/MediaLibrarySession, coordenação da fila e controles externos. |
| `ui` | MediaController/ViewModel/Compose; não consulta API ou arquivos musicais. |

ProgramItem mantém MUSIC, STATION_ID, JINGLE, ADVERTISEMENT e ANNOUNCEMENT. Apenas MUSIC é aceito pelo catálogo atual. Não há vinhetas, jingles ou crossfade nesta etapa.

## Catálogo e fila

O parser valida station/version/items, aceita MUSIC com ID/título/URL HTTPS válidos, ignora itens inválidos e deduplica IDs. Catálogo vazio ou malformado é recusado e não substitui a versão salva. Artista vazio permanece vazio na sessão e é omitido na UI; cada artista desconhecido recebe identidade própria para a fila continuar.

A primeira consulta ocorre ao iniciar a programação. O provider mantém o catálogo em memória e atualiza após **6 horas**; uma nova instância tenta obter a versão atual. Falhas de atualização respeitam intervalo mínimo de **30 segundos**, retornando a última versão válida em memória/disco. Timeouts de conexão/leitura: **10 segundos** cada; resposta limitada a **2 MiB**. Não há consulta a cada frame nem download de MP3 pelo provider.

O último catálogo válido fica em `filesDir/music_catalog.json`, fora do cache de áudio. A gravação usa arquivo temporário e troca atômica; respostas inválidas não apagam dados válidos. Sem catálogo salvo e com falha de rede, a UI mostra **Programação indisponível** e o serviço agenda novas tentativas controladas. URLs e erros internos não aparecem na UI.

A biblioteca completa percorre ciclos sem repetir IDs antes de selecionar todos os itens elegíveis; cada novo ciclo gera outra ordem. Itens ainda pendentes não são duplicados, e a fronteira entre lotes/ciclos evita repetição imediata. Artistas distintos têm preferência, sem bloquear uma biblioteca com artista único ou desconhecido. Versão/itens alterados atualizam a biblioteca lógica na próxima reposição; a faixa atual e sua sessão permanecem.

O ExoPlayer avança naturalmente entre fontes. A fila é reabastecida durante a reprodução; quando termina, a programação continua no mesmo player. O início offline com catálogo salvo prioriza itens que possuem trecho inicial no cache. Isso permite consumir conteúdo preparado, mas não garante reprodução completa de um trecho parcialmente armazenado.

## Controles externos da rádio

`player/RadioSessionCallback.kt`, usado pelo callback da MediaLibrarySession em RadioService,
restringe `availablePlayerCommands` para todos os controladores, incluindo o controlador da
notificação. A sessão oferece Play/Pause e consultas de estado/metadados, sem anterior/próxima,
seek, avanço/retrocesso, alteração da fila, shuffle, repeat ou velocidade. A preparação e a
seleção da estação pela biblioteca permanecem disponíveis; RadioService aceita somente o ID
da estação e conserva a programação e posição existentes.

No Media3 1.6.1, os comandos do controlador da notificação também definem as ações da sessão
nativa do Android. Isso limita notificação, tela bloqueada, Quick Settings, Bluetooth e Android
Auto. Comandos de salto recebidos são ignorados pelo Media3. Nenhum callback depreciado é usado
para autorizar comandos. O ExoPlayer interno permanece com seus comandos completos: fim natural,
recuperação de uma faixa e futuras inserções de ProgramItem continuam na mesma sessão.

## Buffer, preload e cache

Configurações centralizadas em `player/PlaybackConfiguration.kt`:

| Configuração remota | Valor |
| --- | --- |
| Mínimo do DefaultLoadControl | 30 s |
| Histerese do buffer estável | 45–60 s |
| Teto do DefaultLoadControl | 90 s |
| Início de reprodução | 1,5 s |
| Retomada após rebuffer | 3 s |
| Preload da próxima faixa | 45 s |
| Preload da segunda próxima | 15 s |
| Cache LRU | **300 MiB / 314.572.800 bytes** |
| Descarte por idade na próxima inicialização | 24 h |

RadioLoadControl preserva os métodos encaminhados explicitamente ao DefaultLoadControl, incluindo back buffer e lifecycle, evitando o crash corrigido anteriormente. A histerese constrói o buffer progressivamente sem esperar dezenas de segundos para iniciar.

DefaultPreloadManager do Media3 1.6.1 compartilha fontes, allocator e looper com o player. Após 30 s do buffer atual, prepara a próxima e parte da segunda faixa. O restante da biblioteca não é baixado antecipadamente.

SimpleCache + LeastRecentlyUsedCacheEvictor usam o `cacheDir` privado e StandaloneDatabaseProvider. Player/preload compartilham dados e removem os mais antigos por LRU. Chaves incluem ID e hash da URL, evitando reutilizar áudio antigo quando a URL muda com o mesmo ID. O Android pode limpar o cache; após 24 h, uma nova inicialização descarta o cache anterior. Não existe downloader, biblioteca permanente ou opção de download offline.

Queda/troca de rede não limpa timeline/buffer e não recria o player. Áudio disponível continua tocando. Carregamento transitório usa backoff **1, 2, 4, 8, 16, até 32 s**; erros definitivos de URL/formato têm recuperação limitada. A sessão e a posição permanecem durante Wi-Fi/dados móveis. Retomada imperceptível depende do conteúdo realmente preparado e do tempo do retry.

Estados distinguem **Carregando programação**, **Conectando**, **Ao vivo**, **Pausado**, **Reconectando**, **Programação indisponível** e o estado existente **Sem conexão**, quando o áudio preparado esgota. Uma falha de reposição do catálogo não substitui Ao vivo enquanto há reprodução.

## Providers preservados

LocalAssetMusicProvider permanece como fallback **opt-in de desenvolvimento**:

```sh
./gradlew assembleDebug -PuseLocalMusic=true
```

Coloque MP3 em `app/src/main/assets/music/` ou qualquer subpasta, como MPB/rock80/pop. O catálogo local lê título/artista/duração/capa embutida segura, usa nomes como fallback, pula áudio defeituoso e funciona sem rede. Buffer local: mínimo 1 s, máximo 5 s, início 250 ms, rebuffer 500 ms; preload de 3 s / 1 s. UI não acessa arquivos.

O build padrão usa uma tarefa Sync para copiar somente assets não musicais ao diretório gerado; `music/**` fica excluído. **Os MP3 originais não são apagados, movidos ou alterados.** MP3/RAR/APK continuam ignorados pelo Git. Um build opt-in local contém a biblioteca e serve apenas a testes autorizados.

JamendoMusicProvider permanece implementado e inativo. Para uso futuro, mude a composição em RadioApplication e configure `jamendo.clientId` no local.properties ou `JAMENDO_CLIENT_ID` no ambiente; não versione valores. A fila e o player continuam usando os mesmos contratos. Nenhuma licença do MVP é presumida válida para uso comercial futuro.

## GitHub Actions e APK

O workflow existente **Android debug**, `.github/workflows/android-debug.yml`, é o ambiente oficial de validação. Usa Ubuntu, Temurin **JDK 17**, SDK 35, validação do Wrapper e cache de Gradle. Executa **test/testDebugUnitTest → lintDebug → assembleDebug → verificação da assinatura/tamanho/ausência de MP3 → upload**. Não baixa RAR, não extrai músicas e não lê `MUSIC_ARCHIVE_URL`. O secret pode permanecer no repositório, sem ser utilizado.

A cada push em main ou **GitHub → Actions → Android debug → Run workflow → main**, o APK fica em **Artifacts → radio-embarcada-debug-apk**, contendo **app-debug.apk**, por sete dias. Caminho: `app/build/outputs/apk/debug/app-debug.apk`. O workflow falha se houver testes/lint/build com erro ou qualquer MP3 empacotado.

A versão anterior com biblioteca tinha **771.352.284 bytes / 735,6 MiB**. O tamanho atual é registrado no resumo/log de cada execução. APK, músicas e arquivos locais não são versionados.

## Teste manual e validação

1. Instale o APK remoto, abra Rádio Alce conectado e pressione Play. Confira música e avanço automático; artista vazio não deve aparecer.
2. Deixe tocar por 30–60 s para abastecer buffer/preload. Desligue Wi-Fi e dados móveis: o áudio preparado deve continuar até esgotar.
3. Recupere rede antes de esgotar e confira continuidade de posição, música e sessão. Repita Wi-Fi → dados móveis e retorno.
4. Após ouvir faixas e salvar catálogo, reabra temporariamente offline. Trechos já armazenados podem tocar; conteúdo não armazenado exige rede. Instalação nova offline mostra Programação indisponível sem crash.
5. Coloque o app em segundo plano e confira que notificação/player do sistema oferecem somente Play/Pause, sem anterior/próxima ou seek. Bloqueie a tela e repita. Via Bluetooth/Android Auto, confira Play/Pause e metadados; comandos NEXT/PREVIOUS não devem mudar a faixa. Aguarde o fim natural de uma música e confira o avanço automático. Teste também tela apagada, chamadas e Audio Focus. A apresentação exata depende do Android/veículo e precisa de validação em dispositivo.
6. Confira logs de debug com `adb logcat -s RadioDiagnostics`; diagnósticos não aparecem em release/UI.

Os testes JVM não dependem de internet nem dos 77 MP3 reais. Cobrem parsing/validação, catálogo de 77 itens simulado, cache de JSON, fallback offline, refresh/versionamento/backoff, ciclos aleatórios, ProgramItem, estados, buffer e chaves do cache. Também preservam os testes de assets/Jamendo e lifecycle do LoadControl. Testes da sessão usam Robolectric e as ferramentas oficiais de teste do Media3 (somente em testImplementation), com ExoPlayer real e fontes/clock simulados: permissões de controladores comuns e da notificação, Play/Pause, bloqueio de navegação, metadados, avanço interno e fim natural através de MUSIC/STATION_ID/JINGLE. Os scripts antigos de preparação da biblioteca ficam preservados, fora do workflow atual; seus testes podem ser executados com `python3 -m unittest discover -s scripts -p 'test_*.py' -v`.

Lint mantém visíveis os avisos de dependências, serviço exportado para controladores, HTTP herdado e target SDK 35. Manifest, permissões de mídia, MediaSession/MediaLibraryService, Audio Focus e estrutura Android Auto permanecem; a validação automática não substitui teste audível em aparelho. Nenhuma dependência de produção nova foi adicionada.
