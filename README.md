# pnRelics

`pnRelics` добавляет конфигурируемые руны и предметы для Paper 1.16.x–1.21.x.

Руна может выглядеть как B64-голова или как любой Minecraft-материал. Её бонусы работают только тогда, когда руна находится в дополнительной руке — слоте щита и тотема. Кастомные предметы активируются в настроенных слотах рук или брони.

Требуется Java 17 или новее. Для старого Paper 1.16.5, который блокирует запуск на Java 17 собственной проверкой, сервер запускается с аргументом `-DPaper.IgnoreJavaVersion=true`. Современные версии Paper запускаются на требуемой ими версии Java.

## Команды

| Команда | Назначение |
| --- | --- |
| `/pnrelic` | Показать подсказку. |
| `/pnrelic list` | Открыть GUI всех настроенных рун и предметов. |
| `/pnrelic give <игрок> <название> [количество]` | Выдать руну или предмет. |
| `/pnrelic reload` | Перезагрузить конфигурацию и активные бонусы. |

Все команды требуют право `pnrelic.admin`.

## Руны

В `runes.yml` внешний вид задаётся одним из двух способов:

```yaml
runes:
  flame:
    base64: "значение-текстуры"
    name: "&cРуна пламени"
    effects:
      strength: 2

  stone:
    material: PRISMARINE_CRYSTALS
    name: "&dКаменная руна"
    effects:
      resistance: 1
```

## Предметы

В `items.yml` указываются материал, зачарования и слоты активации:

```yaml
items:
  guardian-helmet:
    material: DIAMOND_HELMET
    name: "&bШлем хранителя"
    active-slots: [HELMET]
    enchantments: {protection_environmental: 4, durability: 3}
    attributes:
      max_health: 4.0
    effects:
      night_vision: 1
```

Поддерживаемые слоты: `MAIN_HAND`, `OFF_HAND`, `HELMET`, `CHESTPLATE`, `LEGGINGS`, `BOOTS`.

Атрибут в короткой записи использует обычное сложение. Для другого режима доступна полная запись:

```yaml
attributes:
  movement_speed:
    amount: 0.10
    operation: ADD_SCALAR
```

## Флаги

```yaml
flags: true
```

Скрывает все доступные флаги предмета. Чтобы скрыть только выбранные:

```yaml
flags: [HIDE_ATTRIBUTES, HIDE_UNBREAKABLE]
```

## PNLibrary

PNLibrary встраивается внутрь итогового JAR через Shadow и relocation. Плагин использует единый banner, цвета, GUI-анимацию, точечное обновление страниц и GitHub updater. Отдельно устанавливать PNLibrary на сервер не нужно.

## Сборка

```powershell
./gradlew.bat clean test shadowJar
```

Готовый JAR создаётся в `build/libs/`.
