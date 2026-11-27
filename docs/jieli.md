# JieLi, JL710N e firmware

[Voltar ao projeto](../README.md)

## Identificação da família

O Mali 2 respondeu às consultas de diagnóstico com o código **`0x07`** no Windows e no Android. A tabela da **Zhuhai JieLi Technology** associa esse código à família **JL710N**. O VID Bluetooth SIG `0x05D6` corrobora o fornecedor no registro PnP. O código de família deixa em aberto o part number completo, a revisão física do componente e a revisão da placa. [Protocolo de bancada JieLi, seção 1](https://doc.zh-jieli.com/KDocs/master/%E9%9F%B3%E9%A2%91%E6%8A%80%E6%9C%AF%E5%BA%94%E7%94%A8%E6%96%87%E6%A1%A3%E5%85%A5%E5%8F%A3%E6%B1%87%E6%80%BB/JL-HAT-ANC%E9%AB%98%E6%95%88%E4%BA%A7%E6%B5%8B%E8%87%AA%E9%80%82%E5%BA%94/JL-ANC_%E4%BA%A7%E6%B5%8B%E8%AE%BE%E5%A4%87%E9%80%9A%E4%BF%A1%E5%8D%8F%E8%AE%AEv2.3.5.html)

## O que os números significam

| Dado | Interpretação |
| --- | --- |
| `V2.1.1` | Versão do protocolo de diagnóstico ANC reportada pela unidade. |
| `0x07` | Código de família no diagnóstico de identificação. |
| `0x7100` | Subversão LMP registrada no Windows. Não foi encontrado um mapeamento público para a versão comercial do firmware. |
| `0100` | Campo de revisão de software anunciado em HFP via `AT+XAPL=ABCD-1234-0100,10`. A mensagem inteira coincide com o exemplo Apple. As duas capturas verificam o valor anunciado. Sua relação com o firmware instalado ficou sem confirmação. |

Em particular, os bytes observados deixam em aberto se `0100` é uma revisão real ou um valor genérico de compatibilidade. A evidência disponível não sustenta escrever que o Mali 2 tem firmware **1.0.0**. [Especificação Apple, seção 13.1](https://www.bluetooth.com/wp-content/uploads/attachments/BluetoothDesignGuidelines.pdf)

## O SDK e a busca por uma versão

O SDK Android consultado é o **JieLi Home RCSP 4.2.0**. Ele documenta conexão por SPP/BLE, negociação de sessão, `requestDeviceInfo` e consultas posteriores de estado. A presença dessas funções no SDK indica o que o ecossistema oferece. A unidade precisa confirmar cada função com sua própria resposta. No teste Windows, a consulta de identificação foi observada em HCI, mas ficou sem resposta de aplicação. No Android, o SDK chegou a SPP READY e registrou timeout na mesma consulta. [SDK Android JieLi](https://github.com/Jieli-Tech/Android-JL_Bluetooth) · [Interface DeviceInfo](https://doc.zh-jieli.com/Apps/Android/jielihome/en-us/master/development/api/basic_func_api.html) · [Ensaios](ensaios.md)

As amostras usadas pelos utilitários de arquivo eram **AC6936B, AC6939A e AC6939B**, todas AC693X. A ferramenta leu metadados e checksums dos arquivos offline. A documentação JieLi também descreve leitura de checksum em fluxos específicos de produção. Esse resultado corresponde a um identificador de integridade. [Ferramentas testadas](ferramentas.md) · [Leitura de checksum](https://doc.zh-jieli.com/Tools/zh-cn/firmware_tools/faqs/howto_read_checksum.html)

Até os ensaios registrados, não foi localizado um pacote público oficial para a unidade. Uma consulta de versão atribuível ao fone ou uma informação da Kuba ligada à revisão/lote pode confirmar a versão instalada. Um pacote oficial pode ser analisado offline para identificar sua própria versão e cobertura. [Suporte Kuba](https://kuba.audio/pages/suporte)

## Afinação e medição

A Kuba publica uma resposta acústica medida em simulador IEC 60318-4/711, comparada ao alvo JM-1. A curva é uma referência para medições externas. EQ exposto por aplicativo, afinação de fábrica, filtros ANC e volume interno são parâmetros diferentes e seguem sem leitura. [Especificação comercial](https://kuba.audio/products/mali-2)
