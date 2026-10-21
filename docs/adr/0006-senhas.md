# 0006 — Gerenciador de senhas

**Status:** aceito · 2026-10

## Decisão

- O Gecko descobre formulários de login, preenche e percebe quando um login é enviado; o Zeno só guarda os logins (`Autocomplete.StorageDelegate`) e faz as perguntas (salvar ou atualizar; qual login usar).
- Os logins ficam no banco do Zeno (Room, tabela `logins`, versão 2 com migração automática, sem apagar nada). Usuário e senha são cifrados com AES-256-GCM, com uma chave que não sai do keystore do Android (em hardware, quando o aparelho tem) e um IV aleatório por valor; o site fica em claro, para achar os logins pelo domínio. Copiar o arquivo do banco não revela nenhum usuário ou senha.
- A chave não exige desbloqueio, para o preenchimento não pedir nada a cada página. Ver a lista em Configurações → Senhas pede o bloqueio de tela (biometria ou PIN); sair do app fecha a lista, a tela fica fora de capturas e da lista de apps recentes, e senha copiada vai marcada como sensível. Sem bloqueio de tela não há o que pedir: a lista abre e avisa disso.
- Cada valor é cifrado e decifrado direto no keystore. Para as dezenas de logins de uma pessoa isso custa milissegundos; uma chave de dados intermediária só valeria a complexidade com milhares.
- Perguntas: depois de um login enviado, “Salvar a senha de …?” (com “Nunca neste site”, guardado em `login_exceptions`); num campo de login, uma barra sobre o teclado com os logins salvos, sem cobrir a página. As duas opções (perguntar e preencher sozinho) se desligam em Configurações → Senhas.
- Os logins nunca saem do aparelho: sem sincronização, sem backup em nuvem (`allowBackup=false`). Apagar os dados de navegação não apaga senhas.

## Consequências

- Gerenciadores do sistema (pelo preenchimento automático do Android) continuam funcionando ao lado.
- Trocar de aparelho não leva as senhas; exportar e importar fica para depois.
