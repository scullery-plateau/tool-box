## Fight Club Five

* Item property: un-abbreviate enum set

* Spell components: un-abbreviate enum set, list material components
* Spell classes: split classes into list
* Spell duration: Concentration as separate flag

* Monster abilityScores: parseInt
* Monster stats: parseInt
* Monster stats ac: parse armor notation into separate field
* Monster stats speed: split into map of speeds by type
* Monster actions attack: parse into object {type, attack bonus, damage}
* Monster traits Spellcasting: parse into "level","DC","Spell Attack Bonus"
* Monster spells: split into array
* Monster slots: split into array
* Monster Spellcasting: move Spellcasting trait, spells, and slots into specific object
* Monster actions Multiattack: parse Multiattack into list of allowed attacks
* Monster actions text: parse:
  * attack type, reach, target count, area of effect, damage type
  * - or -
  * area of effect, shape, size, saving throw DC & ability
* Monster legendary: parse as traits
* Monster traits Legendary Resistance: parse into specific property under "legendary"
* Monster bonuses immune: split to list
* Monster bonuses senses: parse to map<type,dist>
* Monster bonuses skill: parse to map<label,bonus>
* Monster bonuses save: parse to map<label,bonus>
* Monster demographics environment, languages: split into array
* Monster demographics languages: none -> empty list

* Class slotsReset: un-abbreviate enum
* Class proficiency, armor, weapons, tools: split to list
* Class 'hd': to "hitDice"
* Class hitDice: parseInt
* Class autoLevel feature: parse as traits
* Class autoLevel scoreImprovement: parse to list of "scoreImprovementLevels" at Class level
* Class autoLevel slots: parse to list of slots by level
* Class autoLevel counter: points by level

* dmgType: un-abbreviate enum
* "dmg": un-abbreviate
* Item value: ?
* Class autoLevel feature: parse subclass (somehow?)
