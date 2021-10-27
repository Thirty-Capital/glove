# SurveyMonkey Extractor [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
### Extrator de dados da SurveyMonkey. 

## How it works

A empresa **SurveyMonkey** tem como objetivo efetuar o desenvolvimento de pesquisas online, com essa plataforma de pesquisa, fica fácil medir e entender feedback de clientes a fim de poder impulsionar o crescimento e a inovação dos negócios.

O **SurveyMonkey Extractor** é uma ferramenta desenvolvida utilizando o framework _mitt_ e possibilita extrair dados da _api_ do _surveymonkey_ (https://developer.surveymonkey.com/). Com ela é possível extrair diversos dados disponíveis na api de maneira bem simplificada (como por exemplo _surveys_ disponíves, detalhes de uma campanha, respostas do clientes em uma campanha etc.), gerando no final um arquivo csv simplificado com o conteúdo desejado.

## Instalação

##### REQUISITOS

- Java 8 +
- Maven
- Git
- Criação de um app na seção My apps (detalhes em https://developer.surveymonkey.com/api/v3/)
- Deploy

##### CONSTRUÇÃO

- Utilizando o [Maven](https://maven.apache.org/): 
    - Acesse o diretório no qual os fontes do **_survey-monkey_ **se localizam.
    - Digite o comando _**mvn package**_.
    - O arquivo **survey-monkey.jar** será gerado no subdiretório **_target_**.

##### CONFIGURAÇÂO

* Crie um arquivo com as credencias para acessar a _api_ através do _access token_ do _app_ criado, este será o seu **credentials file**:

```
{
	"authorization":"bearer <access token>"
}
```

## Utilização

```bash  
java -jar survey-monkey.jar  \
  --credentials="<Identifica o caminho onde o arquivo secreto com as credenciais está localizado>" \
  --output="<Identifica o caminho e nome do arquivo que será gerado>" \
  --field="<Identifica o nome dos campos que serão extraídos, esse nome de campo deve ser passado com ponto (.) quando for json aninhado, nos exemplos explicado melhor essa questão>" \
  --endpoint="<Identifica qual api será chamada, considerar somente a URI, caminho inicial padrão fixo é: https://api.surveymonkey.com/v3/>" \
  --paginate="<(Opcional) aceita os valores true ou false, o padrão é false | Nessa opção você deve informar se o endpoint tem ou não paginação, caso tenha paginação, ele irá percorrer todas as páginas do serviço, se baseando no nó per_page do json>" \
  --parameters="<(Opcional) os parâmetros do serviço deve ser informado nesse parâmetro, deve ser informado em json. Olhar exemplos.>" \
  --partition=<(Opcional) Partição, dividos por + quando for mais de um> \
  --key=<(Opcional) Chave única, dividos por + quando for mais de um>
```

## Exemplos

##### Pegar surveys (https://developer.surveymonkey.com/api/v3/#api-endpoints-get-surveys)

```bash
java -jar /home/etl/lib/survey-monkey.jar  \
  --credentials="/<location>/surveymonkey.json" \
  --output="/tmp/surveymonkey/surveys/surveys.csv" \
  --endpoint="surveys" \
  --field="data.id+data.title" \
  --key="::checksum()" \
  --partition="::fixed(full)" \
  --paginate="true"
```

* No exemplo acima informamos no parâmetro _field_ dois campos que queremos o retorno separados por ponto, isso é feito pois o retorno da _api_ é um _json_ aninhado, então informamos o ponto como um caminho até chegar no resultado desejado. A _Api_ retorna um _json_ da seguinte maneira:

```json
{
  "data": [
    {
      "id": "1234",
      "title": "My Survey",
      "nickname": "",
      "href": "https://api.surveymonkey.com/v3/surveys/1234"
    }
  ],
  "per_page": 50,
  "page": 1,
  "total": 1,
  "links": {
    "self": "https://api.surveymonkey.com/v3/surveys?page=1&per_page=50",
    "next": "https://api.surveymonkey.com/v3/surveys?page=2&per_page=50",
    "last": "https://api.surveymonkey.com/v3/surveys?page=5&per_page=50"
  }
}
```

* Perceba que para pegar por exemplo, o **id** da _survey_ inicia no _array_ _"data"_ e depois _"id"_, por isso colocamos _data.id_, mesma coisa para pegar o título da _survey_, o _array_ inicia com _"data"_, depois _"title"_, por isso _data.title_.





## Contributing, Bugs, Questions
Contributions are more than welcome! If you want to propose new changes, fix bugs or improve something feel free to fork the repository and send us a Pull Request. You can also open new `Issues` for reporting bugs and general problems.
