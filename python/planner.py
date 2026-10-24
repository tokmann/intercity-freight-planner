"""Операции обработки заявки на перевозку и планирования рейса.

Процедурная реализация: только функции и стандартные структуры данных.
Каждая сущность логической модели хранится как словарь (dict),
набор сущностей – как список (list) или словарь по ключу.
"""
import math
from datetime import timedelta

# Неизменяемые параметры планирования
AVERAGE_SPEED_KMH = 60          # средняя техническая скорость на магистрали, км/ч
DRIVING_BEFORE_BREAK_H = 4.5    # управление до специального перерыва, ч
BREAK_H = 0.75                  # специальный перерыв, ч
DAILY_DRIVING_H = 9             # управление в течение ежедневного периода, ч
DAILY_REST_H = 11               # ежедневный отдых, ч
DANGEROUS_PERMIT_CODE = "ДОПОГ"
REFRIGERATOR = "Рефрижератор"
VEHICLE_OK = "исправен"

REQUIRED_FIELDS = (
    ("client_id", "клиент"),
    ("body_type_id", "тип кузова"),
    ("loading_address", "адрес погрузки"),
    ("unloading_address", "адрес выгрузки"),
    ("loading_time", "время погрузки"),
    ("delivery_deadline", "срок доставки"),
    ("cargo_mass_t", "масса груза"),
    ("cargo_volume_m3", "объем груза"),
    ("places", "число мест"),
    ("packaging", "вид упаковки"),
    ("distance_km", "расстояние"),
)
POSITIVE_FIELDS = (
    ("cargo_mass_t", "масса груза"),
    ("cargo_volume_m3", "объем груза"),
    ("places", "число мест"),
    ("distance_km", "расстояние"),
)


def index_by(records, key):
    """Словарь «значение ключа -> запись»."""
    result = {}
    for record in records:
        result[record[key]] = record
    return result


def next_id(records, key):
    """Следующее свободное значение суррогатного ключа."""
    max_id = 0
    for record in records:
        if record[key] > max_id:
            max_id = record[key]
    return max_id + 1


def set_status(requests, request_id, status):
    """Возвращает новый список заявок, в котором у заявки изменен статус."""
    result = []
    for request in requests:
        if request["request_id"] == request_id:
            changed = dict(request)
            changed["status"] = status
            result.append(changed)
        else:
            result.append(request)
    return result


# ---------- Операция «Зарегистрировать заявку» ----------

def register_request(requests, raw_request):
    """Присваивает заявке код и статус, добавляет ее в журнал заявок."""
    request = dict(raw_request)
    request["request_id"] = next_id(requests, "request_id")
    request["status"] = "зарегистрирована"
    return requests + [request], request


# ---------- Операция «Проверить комплектность заявки» ----------

def check_request(request, clients_by_id, body_types_by_id):
    """Возвращает список замечаний; пустой список – заявка заполнена полностью."""
    problems = []
    for field, title in REQUIRED_FIELDS:
        value = request.get(field)
        if value is None or value == "":
            problems.append("не указано: " + title)
    if problems:
        return problems

    if request["client_id"] not in clients_by_id:
        problems.append("клиент не найден в базе")
    body_type = body_types_by_id.get(request["body_type_id"])
    if body_type is None:
        problems.append("неизвестный тип кузова")
    elif body_type["name"] == REFRIGERATOR and not request.get("temperature_mode"):
        problems.append("не указано: температурный режим")
    for field, title in POSITIVE_FIELDS:
        if request[field] <= 0:
            problems.append("неверное значение: " + title)
    if request["delivery_deadline"] <= request["loading_time"]:
        problems.append("срок доставки не позже времени погрузки")
    return problems


# ---------- Операция «Рассчитать время в пути» ----------

def calc_travel_time(distance_km, speed_kmh=AVERAGE_SPEED_KMH):
    """Время в пути, ч, с учетом специальных перерывов и ежедневного отдыха водителя."""
    driving = distance_km / speed_kmh
    days = math.ceil(driving / DAILY_DRIVING_H)
    last_day = driving - DAILY_DRIVING_H * (days - 1)
    breaks_per_full_day = math.ceil(DAILY_DRIVING_H / DRIVING_BEFORE_BREAK_H) - 1
    breaks_last_day = math.ceil(last_day / DRIVING_BEFORE_BREAK_H) - 1
    breaks = breaks_per_full_day * (days - 1) + breaks_last_day
    rests = days - 1
    return driving + breaks * BREAK_H + rests * DAILY_REST_H


def intervals_overlap(start1, end1, start2, end2):
    """Пересекаются ли интервалы [start1, end1) и [start2, end2)."""
    return start1 < end2 and start2 < end1


# ---------- Операция «Подобрать автомобиль» (алгоритм 1) ----------

def vehicle_fits(vehicle, request):
    """Автомобиль исправен, кузов нужного типа, грузоподъемности и объема хватает."""
    return (vehicle["tech_state"] == VEHICLE_OK
            and vehicle["body_type_id"] == request["body_type_id"]
            and vehicle["capacity_t"] >= request["cargo_mass_t"]
            and vehicle["volume_m3"] >= request["cargo_volume_m3"])


def select_vehicle(request, vehicles, trips, start, end):
    """Свободный подходящий автомобиль с наименьшим запасом грузоподъемности."""
    best = None
    best_reserve = 0
    for vehicle in vehicles:
        if vehicle_fits(vehicle, request):
            busy = False
            i = 0
            while i < len(trips) and not busy:
                trip = trips[i]
                if (trip["vehicle_id"] == vehicle["vehicle_id"]
                        and intervals_overlap(trip["departure_time"], trip["arrival_time"], start, end)):
                    busy = True
                i += 1
            if not busy:
                reserve = vehicle["capacity_t"] - request["cargo_mass_t"]
                if best is None or reserve < best_reserve:
                    best = vehicle
                    best_reserve = reserve
    return best


# ---------- Операция «Подобрать водителя» ----------

def required_permits(vehicle, request, permits):
    """Коды допусков, без которых водителя нельзя поставить на рейс."""
    result = {vehicle["required_permit_id"]}
    if request.get("dangerous", False):
        for permit in permits:
            if permit["code"] == DANGEROUS_PERMIT_CODE:
                result.add(permit["permit_id"])
    return result


def select_driver(vehicle, request, drivers, permits, driver_permits, trips, trip_drivers, start, end):
    """Водитель с нужными действующими допусками, успевающий отдохнуть; при выборе – наименее загруженный."""
    needed = required_permits(vehicle, request, permits)
    trips_by_id = index_by(trips, "trip_id")
    rest = timedelta(hours=DAILY_REST_H)
    best = None
    best_load = 0
    for driver in drivers:
        valid = set()
        for driver_permit in driver_permits:
            if (driver_permit["driver_id"] == driver["driver_id"]
                    and driver_permit["valid_until"] >= end.date()):
                valid.add(driver_permit["permit_id"])
        if needed <= valid:
            busy = False
            load = 0
            for assignment in trip_drivers:
                if assignment["driver_id"] == driver["driver_id"]:
                    trip = trips_by_id[assignment["trip_id"]]
                    load += 1
                    if intervals_overlap(trip["departure_time"], trip["arrival_time"], start - rest, end + rest):
                        busy = True
            if not busy and (best is None or load < best_load):
                best = driver
                best_load = load
    return best


# ---------- Операция «Рассчитать стоимость перевозки» (алгоритм 2) ----------

def index_tariffs(tariffs):
    """Тарифная сетка: тип кузова -> диапазоны расстояний, упорядоченные по началу."""
    grouped = {}
    for tariff in tariffs:
        grouped.setdefault(tariff["body_type_id"], []).append(tariff)
    result = {}
    for body_type_id, bands in grouped.items():
        result[body_type_id] = sorted(bands, key=lambda band: band["dist_from_km"])
    return result


def calc_cost(request, tariff_index):
    """Стоимость по тарифной сетке и признак «требуется согласование»."""
    bands = tariff_index.get(request["body_type_id"], [])
    distance = request["distance_km"]
    low = 0
    high = len(bands) - 1
    band = None
    while low <= high and band is None:
        mid = (low + high) // 2
        if distance < bands[mid]["dist_from_km"]:
            high = mid - 1
        elif distance >= bands[mid]["dist_to_km"]:
            low = mid + 1
        else:
            band = bands[mid]
    if band is None:
        return None, True
    cost = distance * band["rate_per_km"]
    if cost < band["min_cost"]:
        cost = band["min_cost"]
    needs_approval = False
    if request.get("dangerous", False):
        cost = cost * (1 + band["danger_surcharge_pct"] / 100)
        needs_approval = True
    return round(cost, 2), needs_approval


# ---------- Операция «Сформировать рейсовое задание» ----------

def make_trip_task(request, vehicle, driver, start, end, cost, trips, trip_drivers):
    """Создает рейс и назначение водителя; возвращает новые списки и созданные записи."""
    trip = {
        "trip_id": next_id(trips, "trip_id"),
        "request_id": request["request_id"],
        "vehicle_id": vehicle["vehicle_id"],
        "departure_time": start,
        "arrival_time": end,
        "cost": cost,
        "status": "запланирован",
    }
    assignment = {"trip_id": trip["trip_id"], "driver_id": driver["driver_id"], "role": "основной"}
    return trips + [trip], trip_drivers + [assignment], trip, assignment


# ---------- Процесс целиком ----------

def process_request(raw_request, data, tariff_index):
    """Проводит одну входящую заявку по шагам процесса.

    Возвращает новое состояние данных и результат обработки (dict).
    Исходные данные не изменяются.
    """
    clients_by_id = index_by(data["clients"], "client_id")
    body_types_by_id = index_by(data["body_types"], "body_type_id")

    requests, request = register_request(data["requests"], raw_request)
    result = {"request": request, "status": "", "problems": [], "vehicle": None,
              "driver": None, "start": None, "end": None, "cost": None, "trip": None}

    problems = check_request(request, clients_by_id, body_types_by_id)
    if problems:
        result["status"] = "ожидает сведений"
        result["problems"] = problems
        return dict(data, requests=set_status(requests, request["request_id"], result["status"])), result

    start = request["loading_time"]
    end = start + timedelta(hours=calc_travel_time(request["distance_km"]))
    result["start"], result["end"] = start, end

    vehicle = select_vehicle(request, data["vehicles"], data["trips"], start, end)
    if vehicle is None:
        result["status"] = "отклонена"
        result["problems"] = ["нет свободного подходящего автомобиля"]
        return dict(data, requests=set_status(requests, request["request_id"], result["status"])), result
    result["vehicle"] = vehicle

    driver = select_driver(vehicle, request, data["drivers"], data["permits"], data["driver_permits"],
                           data["trips"], data["trip_drivers"], start, end)
    if driver is None:
        result["status"] = "отклонена"
        result["problems"] = ["нет свободного водителя с нужными допусками"]
        return dict(data, requests=set_status(requests, request["request_id"], result["status"])), result
    result["driver"] = driver

    cost, needs_approval = calc_cost(request, tariff_index)
    result["cost"] = cost
    if needs_approval:
        result["status"] = "на согласовании"
        if cost is None:
            result["problems"] = ["расстояние вне тарифной сетки, стоимость назначает начальник отдела эксплуатации"]
        else:
            result["problems"] = ["опасный груз, стоимость согласует начальник отдела эксплуатации"]
        return dict(data, requests=set_status(requests, request["request_id"], result["status"])), result

    trips, trip_drivers, trip, _ = make_trip_task(request, vehicle, driver, start, end, cost,
                                                  data["trips"], data["trip_drivers"])
    result["status"] = "рейс запланирован"
    result["trip"] = trip
    new_data = dict(data, requests=set_status(requests, request["request_id"], result["status"]),
                    trips=trips, trip_drivers=trip_drivers)
    return new_data, result
