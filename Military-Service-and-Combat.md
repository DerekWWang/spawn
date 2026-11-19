---
title: Military Service and Combat
---
{% include nav.html %}

# Military Service and Combat

Any humanoid can raise soldiers, and soldiers are ordinary actors: they eat, drink, tire, bleed and lose their nerve like everyone else. A force is only as good as its rations, its kit and its morale. This page covers service, equipment, orders, how each weapon really behaves, and what breaks men. Heavy weapons are on [Siege Artillery](Siege-Artillery).

## Raising soldiers

| Way | Command | What it takes | Starting morale |
| --- | --- | --- | --- |
| Recruit a volunteer | `/survival military recruit <target>` | At least four rations of sound food in your main hand, which the recruit keeps. A player accepts by crouching | 70 |
| Conscript | `/survival military conscript <target>` | You must be armed. An unarmed subject yields at weapon point; an armed one only to a commander with two armed soldiers within reach | 35 |
| Discharge | `/survival military discharge <target>` | | |

A ration is four points of nutrition, whatever the food: nine beetroots are two rations, and what is left over counts towards the next. Rotten food is no ration. What a soldier carried in his pack (arrows, cartridges, flints) falls where he stands when he dies or leaves the service.

## Equipment

`/survival military equip <target>`, or sneak-click your own soldier while holding the item.

| What you hand over | Where it goes |
| --- | --- |
| Armour | Its armour slot |
| A shield | The off hand |
| Any other tool or weapon | The weapon hand |
| Food | The ration pack |
| Arrows | The quiver |
| Paper cartridges | The cartridge box |
| Flint | Spare flints |

Whatever was in the slot comes back to you. Issued kit drops when the soldier dies. A soldier fights with whatever is in hand: a hoe is a poor weapon but it is a weapon.

## Supply

Every five seconds each soldier looks after himself.

- Below 60 hunger he eats a ration from his pack.
- With fewer than four rations he draws food from any vehicle within eight blocks that carries some. An army marches on its wagons: see [Transport and Logistics](Transport-and-Logistics).
- Near water he fills a canteen (four drinks) and drinks from it below 60 thirst.
- With no rations and hunger below 35, or no water and thirst below 45, a soldier on a `hold`, `follow`, `guard` or `move` order breaks ranks to forage for himself.

## Orders

`/survival military order <soldiers> <order>`

`<soldiers>` is one soldier's name or UUID, or `all` for every soldier of yours within ninety-six blocks. A selector such as `@e` needs operator rights. A soldier who cannot reach the place he was sent holds where he is and tells you. A player who enlisted leaves with `/survival military resign`.

| Order | Effect |
| --- | --- |
| `hold` | Stand and defend the spot |
| `follow` | Stay with the commander |
| `retreat` | Fall back to the commander |
| `move <pos> [line\|column\|square]` | March to a point in formation |
| `guard <pos> [radius]` | Hold an area and engage hostile sides that enter it |
| `attack <target>` | Engage: shoot if able, otherwise close |
| `charge <target>` | Close to hand-to-hand regardless of missile weapons |
| `fire <target>` | Shoot from where they stand; never close |
| `mount <animal or vehicle>` and `dismount` | Horses must be tamed, saddled and riderless; a vehicle needs a free seat. Nothing else is ridden |
| `crew <piece>` | Serve an artillery piece |
| `bombard <piece> <pos>` | Lay a piece on a point and keep firing. Given standing within eight blocks of the piece, and refused if the point is out of its reach |
| `drive <vehicle> <pos>` | Drive a vehicle to a point |
| `work <pos>` | Labour at a works |

`/survival military hostile <target>` marks another commander's side as the enemy for guards, and `peace` withdraws it. `/survival military roster` lists every soldier's morale, order, kit, supply, health and skills.

## Weapons

### Hand to hand

Blows use the weapon's real damage and speed, the soldier's manipulation and Melee skill, and the defender's armour. A shield-bearer facing the blow may parry it. A spear couched from a moving horse strikes with the horse's speed added, and cavalry that rides onto levelled spears takes the points first. Fighting practises Melee.

### Bow and crossbow

| Weapon | Engagement range | Notes |
| --- | --- | --- |
| Bow | 24 blocks | Faster |
| Crossbow | 40 blocks | Slower, flatter |

Each shot is a real arrow from the quiver, and spent arrows can be picked up. An archer ordered to `fire` with an empty quiver holds his ground; ordered to `attack`, he closes with whatever is in his hand.

### The musket

The musket is a flintlock smoothbore. It is made at a workshop from a barrel, lock and stock (Smithing 12) and fires paper cartridges, made ninety-six at a time from 96 balls, a kilogram of powder and 24 sheets of paper: a 31 g ball over six drams of powder.

| Property | Value |
| --- | --- |
| Loading | 18 seconds for a fully drilled man (Marksmanship 20), 30 for a raw recruit. A player holds use through the whole drill, then uses again to fire. Letting go abandons the load |
| Range | Soldiers open fire at 48 blocks. The ball carries 120 |
| Damage | 18 inside 20 blocks, falling to 6 at 100. It ignores shields |
| Accuracy | Aim error of 1.6 degrees unskilled, 0.5 at Marksmanship 20, and 1.8 times worse from horseback. A volley weapon beyond about thirty blocks |
| Misfire | 1 in 10, 1 in 2 in rain, plus one percentage point for every round since the barrel was cleaned. A misfire spends the cartridge |
| Fouling | Each round fired slows the next load by 4%. At 40 rounds the barrel will not load at all |
| Cleaning | Sneak and use for one minute (longer if unskilled). Soldiers stop to clean at 30 rounds |
| Flint | Good for 40 shots. Then the lock gives no spark until a spare flint is fitted from the inventory (soldiers use their spare flints). The charge stays in the barrel |
| Wear | Each shot wears the piece |

Gunfire stampedes loose animals within ten blocks and shakes the morale of men it lands near.

### Mounted troops and chariots

A soldier ordered to `mount` a tamed, saddled horse fights from the saddle. A rider who takes 3 or more damage is thrown with a chance of 30%, less 1.5 points for each level of Riding. A chariot carries a driver and one fighter who shoots from the car: fast on a road, crippled off it.

## Morale

Morale runs from 0 to 100 and is what keeps a soldier in the ranks.

| Every five seconds | Change |
| --- | --- |
| Hunger below 25 | -1.5 |
| Thirst below 25 | -1.5 |
| Bleeding | -1 |
| Near the commander | +0.25 |
| Away from the commander | -0.1 for a volunteer, -0.5 for a conscript |
| Fed and watered (both above 60) | Drifts 2% of the way toward 75 for a volunteer, 45 for a conscript |

| Event | Change |
| --- | --- |
| Wounded | -1.2 per point of damage for a volunteer, -1.8 for a conscript |
| A comrade killed nearby | -8 |
| An enemy killed nearby | +4 |
| Musket ball landing close | -1 to -2 |
| Round shot or shell landing close | -3 to -6 |

**Rout.** A soldier whose morale is below 20 when he is hit breaks and runs for twenty seconds. Within 24 blocks of the commander he rallies and his morale is lifted to at least 25; out of reach and still below 20, he keeps running.

**Desertion.** Away from the commander, a conscript below 15 or a volunteer below 5 leaves the service for good.

## Skills that matter

Melee, Marksmanship, Riding and Gunnery are learned by doing them. See [Skills and Learning](Skills-and-Learning).

## Common mistakes

- Conscripting more men than you can feed. Hungry conscripts out of your sight are gone within minutes.
- Issuing muskets without cartridges and flints.
- Ordering musketeers to `charge`. They will, with the butt.
- Sending archers to `attack` with empty quivers.
- Leading a force away from water with no canteens filled.
