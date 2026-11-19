---
title: Logistics and Records
---
{% include nav.html %}

# Logistics and Records

Every quantity a society has exists twice.

- The **ground truth** is what is really there. The game counts it from the chests of storage buildings, the animals in pens, the residents in homes, and everything gathered, made, used and spoiled.
- The **observed truth** is what the society's records say. It is all a manager ever sees: the journal's *Ledger* tab and `/ledger report`.

They drift apart the way real books do. Goods moving in and out of a store are entered with error. The clerks have habits, so the error leans one way. What rots is never entered at all, so stores hold less than the books claim. Headcounts are a fresh, imperfect census each morning.

## How good the books are

How wrong the books are depends on what the society can count with.

| Records kept by | Learned with | Typical error |
| --- | --- | --- |
| Memory and guesswork | | 40% |
| Tally marks | Counting and tally | 25% |
| Written records | Writing | 15% |
| Written accounts with arithmetic | Arithmetic | 10% |
| Accounts in standard measures | Weights and measures | 7% |
| Paper ledgers | Papermaking | 5% |
| Ledgers audited by sampling | Probability and statistics | 3% |
| Machine-added ledgers | Calculator making | 2% |
| Computer records | Vacuum-tube computer building | 0.5% |
| A networked database | Server | 0.1% |

## Stock-takes

A **stock-take** (`/ledger stocktake`, or *Take stock* on a store's cornerstone) sends someone to count the shelves, and corrects the books to the count. The counter has to be at the store.

- A count from memory is twice as good as the running records.
- A society with Writing does twice as well again on a clay tablet, and three times as well on paper and ink. The tablet, or the paper and ink, is used up.
- A store may be counted once a game hour.

## Goods that spoil

Food is aged by the survival rules: every edible thing has its own shelf life, and stores slow it ([Water Pottery and Food](Water-Pottery-and-Food)).

Goods the survival rules do not age spoil here:

| Goods | Half-life |
| --- | --- |
| Vaccines, insulin and recombinant medicines, which need a cold chain | 6 days |
| Raw hides, animal fat and penicillin | 30 days |

A sound, enclosed store halves the rate, and a cold store nearby cuts it to a tenth. Goods in a player's pockets and in loose chests spoil at the full rate. You find out what has gone off when you open the container.

## Animals

The census counts each pen's animals by kind, with those carrying young and those still young, and tells animals herded in from animals born. Breeding itself follows the survival husbandry ([Agriculture and Livestock](Agriculture-and-Livestock)).

## Commands

| Command | What it does |
| --- | --- |
| `/ledger report` | What the books say |
| `/ledger census` | Count people and animals now |
| `/ledger stocktake <cornerstone>` | Count a store |
| `/ledger get <metric>` | One recorded figure as the command's result, for example `stock/minecraft:iron_ingot`, `people/alive` or `herd/cow` |
| `/ledger truth [player]`, `/ledger history [days]` | Operators only: the real figures beside the recorded ones |

Server owners can tune `spoilageRate` and `recordKeepingError`.

## Not simulated yet

- **Workers.** There are no worker entities yet: players, or bots playing as players, farm, haul, count and build. The engines already give what workers will need: bills of materials and missing-block lists for builders, stock by store for haulers, stock-takes for clerks.
- **Distribution.** Residents do not yet eat from stores, so nothing forces food to be where the people are. The books already say where everything is.
