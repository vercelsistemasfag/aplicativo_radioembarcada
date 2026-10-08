# Rádio Embarcada — Instruções para Agentes

## Objetivo do projeto
Plataforma de rádio embarcada para motoristas de aplicativos locais de mobilidade.

Um único aplicativo Android deve atender múltiplos clientes (multi-tenant), cada um com identidade visual, programação, vinhetas, jingles e campanhas próprias.

## Princípios
- App simples e seguro para uso no carro.
- Programação automática, sem escolha manual de playlists.
- Um único APK para todos os clientes.
- Configuração carregada dinamicamente pelo servidor.
- Reprodução contínua 24/7.
- Funcionamento em segundo plano.
- Arquitetura preparada para Android Auto.
- Evitar complexidade fora do escopo do MVP.

## Stack inicial
- Android
- Kotlin
- Jetpack Compose
- AndroidX Media3
- ExoPlayer
- MediaSession
- MediaLibraryService
- Foreground Service quando necessário

## Multi-tenant
Cada cliente deve ter:
- id
- nome
- código de ativação
- logotipo
- cores
- nome da rádio
- stream próprio
- vinhetas próprias
- jingles próprios
- campanhas próprias
- configurações próprias

Não criar APK separado por cliente.

## Ativação
No primeiro acesso, o motorista informa um código da empresa.

Exemplo: `ALCE2026`

A API retorna:
- clientId
- companyName
- radioName
- logoUrl
- primaryColor
- streamUrl

A configuração deve ser armazenada localmente.

## Programação
A programação NÃO é montada no app.

O servidor entrega um stream final com:
- músicas
- vinhetas
- jingles
- publicidade
- avisos institucionais

O aplicativo apenas reproduz o stream.

## Player
Usar Media3.

Separar claramente:
- UI
- player
- API
- modelos
- armazenamento local
- configuração do tenant

A UI não deve controlar diretamente detalhes internos do ExoPlayer.

## Reconexão
Implementar retry/backoff para falhas temporárias de internet.

Estados previstos:
- Conectando
- Ao vivo
- Pausado
- Sem conexão
- Reconectando

## Interface
Tela principal:
- logo
- nome da rádio
- indicador AO VIVO
- play/pause
- conteúdo atual quando disponível
- status da conexão

Evitar menus complexos e elementos pequenos.

## Android Auto
Preparar desde o início:
- MediaSession
- MediaLibraryService
- metadados
- controles padrão

Não criar UI automotiva fora das APIs permitidas.

## Bluetooth
Usar saída de áudio padrão do Android.

Não implementar lógica Bluetooth proprietária sem necessidade.

## Áudio
Respeitar:
- Audio Focus
- chamadas
- outros players
- troca de saída de áudio
- conexão/desconexão de dispositivos

## Backend
Não implementar backend definitivo na primeira etapa.

Pode usar:
- JSON local
- mocks
- endpoint de teste

## Segurança
Nunca incluir no código:
- senhas
- tokens permanentes
- chaves privadas
- credenciais
- secrets

## Estrutura sugerida

```text
app/
  src/main/java/.../
    ui/
    player/
    data/
    network/
    model/
    storage/
```

## Regras para o Codex
Antes de mudanças relevantes:
1. Ler este arquivo.
2. Ler `docs/MVP.md`.
3. Inspecionar a arquitetura atual.
4. Preservar funcionalidades existentes.
5. Implementar apenas o necessário.
6. Rodar testes.
7. Rodar build.
8. Corrigir erros antes de encerrar.
9. Documentar decisões arquiteturais importantes.

## Primeira implementação
Foco:
- projeto Android compilável
- Kotlin
- Jetpack Compose
- Media3
- stream de teste
- reprodução em segundo plano
- MediaSession
- MediaLibraryService
- base para Android Auto
- configuração mockada de um tenant

Ainda NÃO implementar:
- backend definitivo
- painel administrativo
- publicidade
- relatórios
- cobrança
- catálogo musical
