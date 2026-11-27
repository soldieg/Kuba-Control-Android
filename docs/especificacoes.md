# Especificações do Kuba Mali 2

[Voltar ao projeto](../README.md)

Os resultados de diagnóstico abaixo vêm de uma unidade. A página da Kuba publica Bluetooth 6.0 com multiponto, ANC, proteção IPX4 e estojo de recarga USB-C. Esses são dados de catálogo. As observações da unidade estão registradas separadamente. [Kuba Mali 2](https://kuba.audio/products/mali-2)

| Item | Valor ou resultado observado | Origem |
| --- | --- | --- |
| Nome e transporte | **Kuba Mali 2**, emparelhamento Bluetooth Classic | Enumeração Windows e Android |
| Identificação de produto | Bluetooth SIG VID `0x05D6` (JieLi), PID `0x000A` | Registro PnP associado à unidade no Windows |
| Família do chip | `0x07`, associada a **JL710N** | Resposta do diagnóstico Windows e Android e tabela JieLi |
| Revisão Bluetooth | LMP `0x0E` (Bluetooth 6.0), subversão `0x7100` | Informação do rádio no Windows |
| Porta serial | `JL_SPP`, UUID `0x1101`, canal RFCOMM 1, perfil 1.2 | SDP sem cache. COM5 foi a porta atribuída na máquina do ensaio |
| Hands-Free | `JL_HFP`, UUID `0x111E`, canal RFCOMM 4, perfil 1.9 | SDP sem cache |
| Áudio | A2DP negociou **AAC** em uma sessão Windows | Captura HCI e decodificação AVDTP |
| Controle de mídia | AVRCP **Play** `0x44` e **Pause** `0x46`, com pressionar e soltar | Captura HCI no sentido fone → PC |
| Bateria geral | HFP reportou `9/9`. O Windows registrou 80% em outra sessão e o Android registrou 60% em um ensaio anterior | Mensagem do fone e leituras do sistema em horários diferentes |
| Diagnóstico ANC | Protocolo **V2.1.1** | Resposta direta pelo SPP no Windows e no Android |
| Identificação Hands-Free | `AT+XAPL=ABCD-1234-0100,10` | Duas reconexões Windows. O campo de software anunciado foi `0100` |

## Dados ainda sem leitura

- Versão instalada do firmware, revisão física exata do chip e da placa e imagem do firmware. Consulte a [análise do 0100](jieli.md).
- Baterias esquerda, direita e do estojo, carregamento e sensor de encaixe.
- Modo acústico atual, configuração de transparência, EQ interno, gestos e modo jogo. Os modos acústicos foram acionados no fone, sem um campo correspondente identificado na captura Windows.
- Endereço BLE atribuído à unidade e disponibilidade da característica GATT de revisão de firmware `0x2A26`.
- Interface de dados USB do estojo. O USB-C para recarga consta na documentação comercial.

Para separar dados do sistema operacional, mensagens do fone e condições de cada ensaio, veja [Ensaios e evidências](ensaios.md).
