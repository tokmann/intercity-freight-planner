"""Обработка входящих заявок на междугороднюю перевозку груза и планирование рейсов.

Запуск: python main.py [--fleet ПУТЬ] [--incoming ПУТЬ]
"""
import argparse
import os
import sys

from planner import index_by, index_tariffs, process_request
from storage import load_fleet, load_incoming

BASE_DIR = os.path.dirname(os.path.abspath(__file__))


def fmt_dt(value):
    return value.strftime("%d.%m.%Y %H:%M")


def fmt_hours(hours):
    minutes = round(hours * 60)
    return f"{minutes // 60} ч {minutes % 60:02d} мин"


def fmt_money(value):
    return f"{value:,.2f}".replace(",", " ").replace(".", ",") + " руб."


def fmt_number(value):
    return f"{value:g}".replace(".", ",")


def short_name(driver):
    return f"{driver['last_name']} {driver['first_name'][0]}. {driver['middle_name'][0]}."


def request_lines(request, clients_by_id):
    client = clients_by_id.get(request.get("client_id"))
    client_name = client["name"] if client else "клиент не указан"
    lines = [f"Заявка № {request['request_id']} от {fmt_dt(request['received_at'])}, {client_name}"]
    if request.get("loading_address") and request.get("unloading_address"):
        distance = request.get("distance_km")
        tail = f" ({fmt_number(distance)} км)" if distance else ""
        lines.append(f"  Маршрут: {request['loading_address']} -> {request['unloading_address']}{tail}")
    if request.get("cargo_mass_t") and request.get("cargo_volume_m3"):
        lines.append(f"  Груз: {fmt_number(request['cargo_mass_t'])} т, {fmt_number(request['cargo_volume_m3'])} м3"
                     + (", опасный" if request.get("dangerous", False) else ""))
    return lines


def result_lines(result, data):
    clients_by_id = index_by(data["clients"], "client_id")
    lines = request_lines(result["request"], clients_by_id)
    if result["start"] is not None:
        hours = (result["end"] - result["start"]).total_seconds() / 3600
        lines.append(f"  Время в пути: {fmt_hours(hours)} (погрузка {fmt_dt(result['start'])}, "
                     f"прибытие {fmt_dt(result['end'])})")
    vehicle = result["vehicle"]
    if vehicle is not None:
        lines.append(f"  Автомобиль: {vehicle['model']}, {vehicle['plate']} "
                     f"({fmt_number(vehicle['capacity_t'])} т, {fmt_number(vehicle['volume_m3'])} м3)")
    driver = result["driver"]
    if driver is not None:
        lines.append(f"  Водитель: {short_name(driver)}, тел. {driver['phone']}")
    if result["cost"] is not None:
        lines.append(f"  Стоимость: {fmt_money(result['cost'])}")
    lines.append(f"  Статус: {result['status']}")
    for problem in result["problems"]:
        lines.append(f"    - {problem}")
    trip = result["trip"]
    if trip is not None:
        request = result["request"]
        client = clients_by_id[request["client_id"]]
        lines.append(f"  Рейсовое задание № {trip['trip_id']}:")
        lines.append(f"    водитель {short_name(driver)}, автомобиль {vehicle['plate']}")
        lines.append(f"    погрузка {fmt_dt(trip['departure_time'])}: {request['loading_address']}")
        lines.append(f"    выгрузка до {fmt_dt(request['delivery_deadline'])}, "
                     f"расчетное прибытие {fmt_dt(trip['arrival_time'])}: {request['unloading_address']}")
        lines.append(f"    контакт грузоотправителя: {client['contact_person']}, {client['phone']}")
    return lines


def main(argv):
    parser = argparse.ArgumentParser(description="Обработка заявок на междугороднюю перевозку груза")
    parser.add_argument("--fleet", default=os.path.join(BASE_DIR, "data", "fleet.json"),
                        help="файл с клиентами, автомобилями, водителями, тарифами и рейсами")
    parser.add_argument("--incoming", default=os.path.join(BASE_DIR, "data", "incoming.json"),
                        help="файл с входящими заявками")
    args = parser.parse_args(argv)

    data = load_fleet(args.fleet)
    incoming = load_incoming(args.incoming)
    tariff_index = index_tariffs(data["tariffs"])
    results = []
    for raw_request in incoming:
        data, result = process_request(raw_request, data, tariff_index)
        results.append(result)
        print("\n".join(result_lines(result, data)))
        print()

    print("Итог:")
    for result in results:
        print(f"  заявка № {result['request']['request_id']}: {result['status']}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
