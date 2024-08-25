## Fight Club Five

* Monster stats ac: parse armor notation into separate field
* Monster stats speed: split into map of speeds by type
* Monster actions attack: parse into object {type, attack bonus, damage}
* Monster bonuses immune: split to list

* Monster actions Multiattack: parse Multiattack into list of allowed attacks
* Monster actions text: parse:
  * attack type, reach, target count, area of effect, damage type
  * - or -
  * area of effect, shape, size, saving throw DC & ability
* Monster traits Legendary Resistance: parse into specific property under "legendary"
* Monster bonuses senses: parse to map<type,dist>
* Monster demographics environment, languages: split into array
* Monster demographics languages: none -> empty list

* Monster Innate Spellcasting: parse aspects into spellcasting object

* Monster Spellcasting: parse additional details from "supplemental"

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
