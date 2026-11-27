# Kuba Control Android

Projeto comunitário de interoperabilidade com o **Kuba Mali 2**. O objetivo é criar um aplicativo que leia o estado do fone, identifique o que ele expõe por Bluetooth e amplie as consultas a partir de respostas verificadas. O **Mali 2 Inspector** é o aplicativo Android deste repositório. A investigação também usa ferramentas de diagnóstico no Windows.

[Baixar APK em Releases](https://github.com/soldieg/Kuba-Control-Android/releases) · [Instalação e compilação](docs/aplicativo.md)

## O que já sabemos

- A unidade testada expõe Bluetooth Classic e a porta serial `JL_SPP`. O Windows e o Android leram a identificação de família **JieLi JL710N** e o protocolo de diagnóstico ANC **V2.1.1**.
- A captura Windows identificou **bateria geral**, comandos **Play/Pause** enviados pelo fone e negociação de áudio **AAC**.
- O fone anunciou **`0100`** via Hands-Free. A mensagem coincide com o exemplo da especificação Apple. A versão instalada do firmware continua sem confirmação.
- Bateria de cada lado e do estojo, modo ANC ativo, EQ, gestos configuráveis e modo jogo ainda precisam de uma leitura atribuível à unidade. A consulta de identificação `GetTargetInfo` saiu pelo canal serial em um ensaio Windows e ficou sem resposta de aplicação.

## Documentação

| Página | Conteúdo |
| --- | --- |
| [Especificações do Mali 2](docs/especificacoes.md) | Identificação, serviços Bluetooth, áudio, bateria e o que permanece desconhecido. |
| [Ensaios e evidências](docs/ensaios.md) | O que foi testado no Windows e Android, resultados positivos e negativos e como interpretá-los. |
| [Aplicativo Android e trabalho no Windows](docs/aplicativo.md) | Funções reais, demonstração, instalação, build, código e diferenças entre plataformas. |
| [Ferramentas e projetos encontrados](docs/ferramentas.md) | Ferramentas de captura, catálogo de utilitários JieLi e referências comunitárias. |
| [JieLi, JL710N e firmware](docs/jieli.md) | Fabricante, identificação do chip, SDK, limites dos arquivos de exemplo e fontes de versão. |
