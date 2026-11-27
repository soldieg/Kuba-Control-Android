# Aplicativo Android e trabalho no Windows

[Voltar ao projeto](../README.md)

## Android

O **Mali 2 Inspector 1.1.5** requer Android 8 ou posterior. No Android 12 ou posterior, o sistema pede permissão de dispositivos próximos para as ações Bluetooth. Baixe o APK pela página de [Releases](https://github.com/soldieg/Kuba-Control-Android/releases).

| Ação | Comportamento |
| --- | --- |
| **Selecionar este fone e ler dados Android** | Lista o fone emparelhado e os serviços conhecidos pelo sistema. Usa bateria agregada quando o Android disponibiliza o dado. |
| **Ler família do chip e protocolo ANC** | Usa o RFCOMM seguro do dispositivo selecionado. As respostas de identidade são verificadas por enquadramento, sequência e integridade. A unidade testada retornou JL710N e V2.1.1. |
| **Testar SPP puro — SOMENTE RECEBER** | Abre o socket quando o serviço está disponível e registra o que chega. No teste registrado, não recebeu dados espontâneos. |
| **Conectar e ler ANC, bateria e firmware** | Usa o SDK oficial JieLi com preferência por SPP e a autenticação gerenciada pelo SDK. Registra estágios, erro e opcode. A leitura de identificação RCSP expirou no Mali 2 testado. |
| **Escutar bateria BLE (35 s)** | No Android 12 ou posterior, registra anúncios legados entregues ao app. O decodificador só atribui leituras de bateria cujo endereço embutido corresponda ao fone selecionado. A leitura por peça ainda não foi confirmada. |
| **Controles de mídia** | Operam na sessão de reprodução e no volume do Android. |
| **Abrir demonstração de controles** | Simula localmente bateria, modos acústicos, gestos e EQ de dez bandas. |
| **Exportar relatório JSON** | Guarda estágios da sessão e anúncios entregues ao scanner. O JSON pode incluir dados de dispositivos próximos. Revise-o antes de publicar. |

A exportação mantém até quatro escutas e 512 anúncios por escuta, com contagem de omissões. Os campos incluem bytes brutos, endereço, RSSI, nome e resultado do decodificador. O app usa o seletor de documentos do Android e faz o diagnóstico localmente. A declaração `neverForLocation` pode filtrar alguns anúncios no sistema. [Permissões Bluetooth do Android](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)

### Instalar ou atualizar

1. Emparelhe o Mali 2 nas configurações Bluetooth do Android.
2. Instale o APK de [Releases](https://github.com/soldieg/Kuba-Control-Android/releases) e selecione **Kuba Mali 2** no aplicativo.
3. Para atualizar, continue usando as releases assinadas com a mesma chave. APKs locais ou de CI assinados com outra chave podem exigir desinstalação. Exporte o relatório antes dessa migração.

### Compilar

1. Tenha JDK 17, Android SDK Platform 35 e Build Tools 35 instalados. O script detecta o Android SDK e baixa os AARs da JieLi com verificação de integridade.
2. No Windows, abra [`COMPILAR_APK_WINDOWS.bat`](../COMPILAR_APK_WINDOWS.bat). O APK de depuração é copiado para a raiz da pasta do projeto. O build local usa a chave de depuração do ambiente.
3. No GitHub, também é possível rodar **Build Android APK debug** em Actions. O workflow usa Gradle 8.9, roda os testes Java, verifica a presença das classes do SDK no APK e publica o artefato **Mali2_Inspector_Android_CI_ONLY**. [Workflow](../.github/workflows/build-apk.yml)

### Código e evidências

- [MainActivity.java](../app/src/main/java/com/mali2/inspector/MainActivity.java) reúne interface, Bluetooth Android, mídia e exportação.
- [JieliBridge.java](../app/src/main/java/com/mali2/inspector/JieliBridge.java) implementa a integração com o SDK e os registros RCSP.
- [AndroidManifest.xml](../app/src/main/AndroidManifest.xml) declara as permissões.
- [Testes](../tests/) cobrem identidade, estado de conexão, atribuição de bateria e captura BLE. Os testes do aplicativo são complementares aos ensaios no hardware. [Resultados](ensaios.md)

## Windows

O investigador Windows em Rust foi desenvolvido no workspace da investigação. Este repositório hospeda o código Android e a documentação dos resultados obtidos no computador. O investigador Windows já confirmou SDP, bateria geral via Hands-Free e diagnóstico serial via COM5. Capturas ETW/BTVS foram analisadas no TShark.

Um cliente Windows pode começar com conexão, bateria geral com horário e integração de mídia do sistema. A próxima evidência necessária para oferecer um controle interno do fone é uma resposta atribuível à unidade que descreva o estado ou confirme a operação. [Ensaios](ensaios.md)

## Relação com o projeto iOS

A interface e a divisão de recursos do Kuba Control iOS serviram de referência. O Inspector Android usa seu próprio código e o SDK JieLi. O perfil Mali 2 do projeto iOS estava marcado como não verificado e usava identificadores provisórios. As funções do simulador KubaOpen e seu OTA pertencem ao protótipo. [Auditoria histórica do iOS](../AUDITORIA_KUBA_CONTROL.md)
