# Auditoria do Kuba Control e adaptação Android

Revisão de 26/11/2026. Foram lidos os 40 arquivos rastreados da branch iOS no commit `6b47e34a8eb04266b1e9c2587f632e18baa05ed1`. A adaptação usa o projeto Android deste workspace, com código e interface próprios. A árvore auditada não traz arquivo de licença. [Código revisado](https://github.com/nms6yds2jb-create/Kuba_br_mali2/tree/6b47e34a8eb04266b1e9c2587f632e18baa05ed1)

## O que a branch contém

| Camada | Código revisado | O que funciona e o que se pode aproveitar |
|---|---|---|
| Telas | `App/KubaControlApp.swift`, `App/Views` | Painel com bateria e modos, EQ, remapeamento, firmware e explorador. A organização dos controles orientou a demonstração Android. |
| Bluetooth e sessão | `App/Bluetooth`, `App/HeadsetController.swift` | Exploração CoreBluetooth/GATT, seleção de periférico, notificações e envio por características. A COM5 do Mali 2 usa Classic SPP/RFCOMM. Android e Windows precisam usar seus próprios transportes. |
| Perfis | `App/ProfileStore.swift`, `Profiles`, `CommandProfile.swift`, `ProfileDriver.swift` e `CommandTemplate.swift` | Modelos JSON convertidos em mensagens e eventos. O perfil original do Mali 2 tem `verified: false`, UUIDs provisórios e zero comandos/eventos. O candidato JieLi é BLE, contém uma consulta de identificação e zero decodificadores. KubaOpen é o perfil completo do protocolo inventado para o protótipo. |
| Modelos, EQ e simulação | `Models.swift`, `Equalizer.swift`, `KubaOpenSimulator.swift`, `Hex.swift` e `Checksums.swift` | Modelos de estado, EQ de dez bandas, quantização, checksums e respostas simuladas. Esses conceitos são portáveis. Os limites do EQ e estados simulados não são medições do Mali 2. |
| OTA e C99 | `Firmware.swift`, `App/Views/FirmwareView.swift`, `firmware/kubaopen` | O motor OTA e a implementação C99 usam KubaOpen. Nos testes C, a camada de hardware é simulada. A branch não demonstra integração com rádio, flash ou bootloader do JL710N. |
| Capturas offline | `tools/btsnoop_analyzer.py`, `tools/tests` | Executa em Python no Windows e analisa ATT de capturas btsnoop, incluindo fragmentação ACL. A COM5 usa RFCOMM. O analisador também não aceita o ETL bruto das capturas Windows. |
| Build e documentação | `Package.swift`, `project.yml`, `.github/workflows/ci.yml`, `App/Info.plist`, `README.md`, `docs`, testes Swift e configurações | O pipeline iOS gera um IPA sem assinatura. Os testes usam simulação ou quadros sintéticos. |

[Perfil do Mali 2](https://github.com/nms6yds2jb-create/Kuba_br_mali2/blob/6b47e34a8eb04266b1e9c2587f632e18baa05ed1/Profiles/kuba-mali2.json) · [Revisão do aplicativo iOS](https://github.com/nms6yds2jb-create/Kuba_br_mali2/pull/1)

## Pontos do protótipo relevantes para a adaptação

- O controlador iOS atualiza controles visualmente ao enviar. Para controle real, a interface precisa distinguir intenção, resposta e estado confirmado pelo fone.
- A configuração de GATT dispara consultas logo após pedir notificações. O envio fracionado precisa respeitar a disponibilidade do transporte. O marcador `verified` orienta a seleção e o aviso, sem funcionar como trava de escrita.
- A importação de perfis aceita arquivos com validação semântica limitada. O perfil de demonstração precisa permanecer isolado.
- O OTA aceita confirmação genérica e o C emite sucesso antes de conhecer o resultado da gravação da camada de hardware. O CRC da imagem é calculado novamente a cada bloco. O desenho OTA da branch ficou fora da adaptação.

## Android 1.1 e Windows

No Android, **Ler família do chip e protocolo ANC** usa o socket RFCOMM seguro do dispositivo emparelhado. Foram reaproveitadas as duas consultas documentadas já observadas na COM5. O aplicativo usa o fone emparelhado selecionado e confere o enquadramento, a sequência, o tamanho, o tipo e o CRC da resposta. O teste Java reproduz os quadros da captura Windows e verifica que um CRC inválido impede continuar. O log e a exportação JSON registram confirmação ou falha. O SDK oficial continua disponível para leituras RCSP com autenticação. [RFCOMM no Android](https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices)

**Abrir demonstração de controles** permite testar a interface com bateria virtual, Normal/Transparência/ANC, toque duplo por lado e EQ de dez bandas. Os valores da demonstração ficam no painel local.

No Windows, a **opção 5** continua sendo o caminho já testado no aparelho real. A bateria geral vem do Windows e a COM5 respondeu `V2.1.1` e `JL710N`. O desenho de painel e as validações de estado podem ser reaproveitados em uma interface Windows. O analisador ATT só é aplicável quando houver captura BLE naquele formato. As implementações iOS e KubaOpen não acrescentam comandos validados de ANC, EQ ou toques para a unidade.

## Verificações

O upstream passou nos seis testes Python e na suíte C99 com GCC e HAL simulada no Windows. Swift e Xcode não estavam disponíveis nesta máquina. Na adaptação, a verificação estática e o teste Java da captura passaram. A conexão Bluetooth pelo APK depende de teste no celular emparelhado.

O APK Android 1.1.0 foi compilado com Gradle 8.9 e Android SDK 35. A assinatura de depuração v2 foi verificada com apksigner. O manifesto final inclui apenas permissões Bluetooth e não inclui localização ou internet. O ADB não encontrou dispositivos Android conectados. A suíte Rust do Windows passou em 11 testes.
