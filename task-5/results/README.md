# Результаты задания 5

В Spring Batch-приложение добавлены структурированные логи и восемь метрик обработки. Метрики собираются через JMX Exporter и Prometheus, отображаются в Grafana. Логи передаются через Filebeat и Logstash в Elasticsearch и доступны в Kibana. Два правила Grafana выявляют финальный сбой и длительное выполнение задания.

## Артефакты решения

| Артефакт | Расположение |
| --- | --- |
| Java-проект, Dockerfile и сценарии демонстрации | [etl](./etl/) |
| Docker Compose | [docker-compose.yml](./etl/docker-compose.yml) |
| Конфигурация JMX Exporter | [jmx-exporter.yml](./etl/jmx-exporter.yml) |
| Конфигурация Prometheus | [prometheus.yml](./etl/prometheus/prometheus.yml) |
| Дашборд и источник данных Grafana | [provisioning](./etl/grafana/provisioning/) |
| Правила оповещения Grafana | [rules.yml](./etl/grafana/provisioning/alerting/rules.yml) |
| Сбор и обработка логов | [Filebeat](./etl/filebeat/filebeat.yml), [Logstash](./etl/logstash/pipeline/logstash.conf), [шаблон индекса](./etl/logstash/templates/batch-processing.json) |
| Представление логов Kibana для импорта | [batch-processing.ndjson](./etl/kibana/batch-processing.ndjson) |
| Обоснование выбранных метрик | [metrics.ru.md](./metrics.ru.md) |
| Обоснование отправки логов и способа конфигурации оповещений | [task-5.adr.md](./task-5.adr.md) |
| C4-диаграмма с решением наблюдаемости | [observability.component.c4.puml](../../../engineering/preparation/part-2/task-5/diagrams/observability.component.c4.puml) |

Подготовка, запуск и демонстрация описаны в [deployment.md](./deployment.md).

## Скриншоты

1. [Дашборд Grafana: успешная обработка](./screenshhots/01-grafana-dashboard-success.png).
2. [Правила оповещения Grafana в состоянии Normal](./screenshhots/02-grafana-alert-normal.png).
3. [Срабатывание оповещения о финальном сбое](./screenshhots/03-grafana-alert-failure.png).
4. [Срабатывание оповещения о длительном выполнении](./screenshhots/04-grafana-alert-duration.png).
5. [Ошибка обработки в Kibana: идентификаторы выполнения и стек ошибки](./screenshhots/05-kibana-error-log.png).
