# Результаты задания 4

- [ADR: обоснование решения для пакетной обработки](./task-4.adr.md).
- [C4-диаграмма компонентов целевой архитектуры](./.svg/to-be.component.c4.svg).

## Реализация ETL

Доработан Java-шаблон Spring Batch: чтение товаров из `product-data.csv`, обогащение поля `productData` по SKU из таблицы `loyality_data` и запись в `products`. При отсутствии совпадения исходное значение сохраняется. Reader, processor и writer объединены в шаг с транзакционными блоками по три записи; после успешного выполнения приложение выводит сохранённые товары в лог и завершает работу.

Необходимые файлы:

- [Java-проект Spring Batch](./realization/) — исходный шаблон с реализованным ETL-шагом.
- [product-data.csv](./realization/src/main/resources/product-data.csv) — пять исходных товаров.
- [loyality_data.csv](./realization/src/main/resources/loyality_data.csv) — четыре записи для заполнения справочной таблицы.
- [docker-compose.yml](./realization/docker-compose.yml) — PostgreSQL, подготовка БД и запуск ETL в установленном порядке.
- [Dockerfile](./realization/Dockerfile) — сборка JAR и образа приложения в Docker с Java 17.
- [schema-all.sql](./realization/src/main/resources/schema-all.sql) и [db-init.sql](./realization/db-init.sql) — создание бизнес-таблиц и загрузка справочных данных.

Из каталога `realization/task-4/results/realization`:

```sh
docker compose up --build -d
```

## Подтверждение выполнения

- [Скриншот исходных данных](./screenshots/01-inputs.png): товары из `product-data.csv` и справочные данные из `loyality_data`.
- [Скриншот результата ETL](./screenshots/02-results.png): обогащённые товары, сохранённые в таблице `products`.
- [Лог выполнения приложения](./realization/app.log): задание `importProductJob` завершилось со статусом `COMPLETED`.
- [Результат запроса к таблице products](./realization/products-query.txt): сохранены все пять товаров с ожидаемыми значениями.

У товаров с SKU `20001`, `30001` и `50001` значение изменилось с `Loyality_off` на `Loyality_on`. У товара `60001` значение осталось `Loyality_on` после обогащения; у товара `40001`, отсутствующего в справочнике, сохранено исходное `Loyality_on`. Идентификаторы, SKU, названия и количества сохранены.
