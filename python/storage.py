"""Загрузка данных из JSON-файлов в стандартные структуры Python."""
import json
from datetime import date, datetime

DATETIME_FIELDS = ("received_at", "loading_time", "delivery_deadline", "departure_time", "arrival_time")
DATE_FIELDS = ("valid_until",)
COLLECTIONS = ("clients", "body_types", "permits", "vehicles", "drivers", "driver_permits",
               "tariffs", "requests", "trips", "trip_drivers")


def read_json(path):
    with open(path, encoding="utf-8") as file:
        return json.load(file)


def convert_record(record):
    """Строки с датами и временем превращает в date и datetime."""
    result = dict(record)
    for field in DATETIME_FIELDS:
        if isinstance(result.get(field), str) and result[field]:
            result[field] = datetime.fromisoformat(result[field])
    for field in DATE_FIELDS:
        if isinstance(result.get(field), str) and result[field]:
            result[field] = date.fromisoformat(result[field])
    return result


def load_fleet(path):
    """Справочники, автомобили, водители, тарифы, журнал заявок и рейсов."""
    raw = read_json(path)
    data = {}
    for name in COLLECTIONS:
        data[name] = [convert_record(record) for record in raw.get(name, [])]
    return data


def load_incoming(path):
    """Входящие заявки грузоотправителей."""
    return [convert_record(record) for record in read_json(path)]
