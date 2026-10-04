"""Русские названия карт по слову, которое выдаёт модель.

Копия `cr_mod/card_names.py` из основного проекта: сервер должен уметь
переводить названия сам, без зависимости от папки с обучением.
"""

from __future__ import annotations

_ALIASES = {
    "arrows": "arrow",
    "hog": "hog_rider",
    "goblinbarrel": "goblin_barrel",
    "royalghost": "royal_ghost",
    "battleram": "battle_ram",
    "bombtower": "bomb_tower",
    "elixirpump": "elixir_collector",
    "golem_mini": "golemite",
    "infernodragon": "inferno_dragon",
    "mightyminer": "mighty_miner",
    "motherwitch": "mother_witch",
    "ramrider": "ram_rider",
    "rascalboy": "rascal_boy",
    "rascalgirl": "rascal_girl",
    "royale_giant": "royal_giant",
    "skeletonking": "skeleton_king",
    "wallbreaker": "wall_breaker",
    "cursedhog": "royal_hogs",
    "hungry_dragon": "electro_dragon",
    "giant_bomb": "giant_snowball",
    "brawler": "goblin_brawler",
    "zappy": "zappies",
    "goldenknight": "golden_knight",
    "electrogiant": "electro_giant",
    "elixirgolem": "elixir_golem",
    "goblindrill": "goblin_drill",
}

_CARD_RU = {
    "archer": "Лучницы",
    "arrow": "Стрелы",
    "baby_dragon": "Дракончик",
    "balloon": "Шар",
    "bandit": "Бандитка",
    "barbarian": "Варвары",
    "bat": "Летучие мыши",
    "battle_ram": "Боевой таран",
    "bomber": "Подрывник",
    "bomb_tower": "Башня-бомба",
    "bowler": "Вышибала",
    "cannon": "Пушка",
    "flying_machine": "Летучка",
    "king_tower": "Башня короля",
    "princess_tower": "Башня принцессы",
    "goblin_brawler": "Гоблин-задира",
    "dark_prince": "Тёмный принц",
    "dart_goblin": "Гоблин с дротиками",
    "electro_dragon": "Электродракон",
    "electro_giant": "Электрогигант",
    "electro_wizard": "Электромаг",
    "elite_barbarian": "Элитные варвары",
    "elixir_collector": "Сборщик эликсира",
    "elixir_golem": "Эликсирный голем",
    "executioner": "Палач",
    "fire_spirit": "Огненный дух",
    "fireball": "Огненный шар",
    "firecracker": "Огненная лучница",
    "fisherman": "Рыбак",
    "furnace": "Печь",
    "giant": "Гигант",
    "giant_skeleton": "Гигантский скелет",
    "giant_snowball": "Гигантский снежок",
    "goblin": "Гоблины",
    "goblin_barrel": "Бочка с гоблинами",
    "goblin_cage": "Клетка с гоблином",
    "goblin_drill": "Бур с гоблином",
    "goblin_hut": "Хижина гоблинов",
    "golem": "Голем",
    "golemite": "Големчик",
    "golden_knight": "Золотой рыцарь",
    "graveyard": "Кладбище",
    "guard": "Стражи",
    "hog_rider": "Всадник на кабане",
    "hunter": "Охотник",
    "ice_golem": "Ледяной голем",
    "ice_spirit": "Ледяной дух",
    "ice_wizard": "Ледяной колдун",
    "inferno_dragon": "Адский дракон",
    "inferno_tower": "Башня-инферно",
    "knight": "Рыцарь",
    "log": "Бревно",
    "lumberjack": "Дровосек",
    "magic_archer": "Магический лучник",
    "mega_knight": "Мегарыцарь",
    "mega_minion": "Мегаминьон",
    "mighty_miner": "Могучий шахтёр",
    "miner": "Шахтёр",
    "mini_pekka": "Мини-П.Е.К.К.А.",
    "minion": "Миньоны",
    "mortar": "Мортира",
    "mother_witch": "Мать ведьм",
    "musketeer": "Мушкетёр",
    "night_witch": "Ночная ведьма",
    "pekka": "П.Е.К.К.А.",
    "poison": "Яд",
    "prince": "Принц",
    "princess": "Принцесса",
    "rage": "Ярость",
    "ram_rider": "Всадница на баране",
    "rascal_boy": "Разбойник",
    "rascal_girl": "Разбойница",
    "rocket": "Ракета",
    "royal_ghost": "Королевский призрак",
    "royal_giant": "Королевский гигант",
    "royal_hogs": "Королевские кабаны",
    "skeleton": "Скелеты",
    "skeleton_barrel": "Бочка со скелетами",
    "skeleton_king": "Король скелетов",
    "sparky": "Искромёт",
    "spear_goblin": "Гоблины с копьями",
    "tesla": "Тесла",
    "tombstone": "Надгробие",
    "tornado": "Торнадо",
    "valkyrie": "Валькирия",
    "wall_breaker": "Стенобои",
    "witch": "Ведьма",
    "wizard": "Колдун",
    "xbow": "Арбалет",
    "zappies": "Запперы",
}

_SIDES = {
    "ally": "свои",
    "enemy": "противник",
}


def russian_name(model_name: str) -> str:
    """Переводит имя класса модели в русское название карты.

    `witch` -> `Ведьма`. `enemy_hog_rider` -> `Всадник на кабане (противник)`.
    Неизвестное слово возвращается как есть.
    """
    raw = model_name.strip().lower().replace("-", "_").replace(" ", "_")
    side = ""
    for prefix, label in _SIDES.items():
        token = prefix + "_"
        if raw.startswith(token):
            side = label
            raw = raw[len(token) :]
            break

    hidden = raw == "tesla_hidden"
    if hidden:
        raw = "tesla"
    evolved = raw.endswith("_evo")
    if evolved:
        raw = raw[: -len("_evo")]
    raw = _ALIASES.get(raw, raw)

    title = _CARD_RU.get(raw)
    if title is None:
        return model_name
    if evolved:
        title = f"{title}, эволюция"
    if hidden:
        title = f"{title}, скрыта"
    if side:
        title = f"{title} ({side})"
    return title
