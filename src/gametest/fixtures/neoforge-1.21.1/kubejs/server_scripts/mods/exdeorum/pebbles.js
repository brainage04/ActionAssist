BlockEvents.rightClicked(event => {
  const { hand, block, player, item, facing } = event
  if (hand !== 'MAIN_HAND') return
  if (!block.hasTag('minecraft:dirt')) return
  if (!item.isEmpty()) return
  if (player.isShiftKeyDown()) return

  const loot = [
    'exdeorum:andesite_pebble',
    'exdeorum:blackstone_pebble',
    'exdeorum:deepslate_pebble',
    'exdeorum:diorite_pebble',
    'exdeorum:granite_pebble',
    'exdeorum:stone_pebble'
  ]
  const dropItem = Item.of(loot[Math.floor(Math.random() * loot.length)] ?? 'exdeorum:stone_pebble')
  block.popItemFromFace(dropItem, facing)
  player.swing()
})
