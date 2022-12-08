package io.agora.scene.show.beauty

object BeautyCache {

    private val defaultItemValueMap = mapOf(
        Pair(ITEM_ID_BEAUTY_SMOOTH, 0.3f),
        Pair(ITEM_ID_BEAUTY_WHITEN, 0.5f),
        Pair(ITEM_ID_BEAUTY_OVERALL, 0.15f),
        Pair(ITEM_ID_BEAUTY_CHEEKBONE, 0.3f),
        Pair(ITEM_ID_BEAUTY_JAWBONE, 0.46f),
        Pair(ITEM_ID_BEAUTY_EYE, 0.15f),
        Pair(ITEM_ID_BEAUTY_TEETH, 0.2f),
        Pair(ITEM_ID_BEAUTY_FOREHEAD, 0.4f),
        Pair(ITEM_ID_BEAUTY_NOSE, 0.15f),
        Pair(ITEM_ID_BEAUTY_MOUTH, 0.16f),
        Pair(ITEM_ID_BEAUTY_CHIN, 0.46f),

        Pair(ITEM_ID_EFFECT_BAIXI, 0.5f),
        Pair(ITEM_ID_EFFECT_TIANMEI, 0.5f),
        Pair(ITEM_ID_EFFECT_CWEI, 0.5f),
        Pair(ITEM_ID_EFFECT_YUANQI, 0.5f),

        Pair(ITEM_ID_FILTER_CREAM, 0.4f),
        Pair(ITEM_ID_FILTER_MAKALONG, 0.4f),
        Pair(ITEM_ID_FILTER_OXGEN, 0.4f),
        Pair(ITEM_ID_FILTER_WUYU, 0.4f),
        Pair(ITEM_ID_FILTER_Po9, 0.4f),
        Pair(ITEM_ID_FILTER_LOLITA, 0.4f),
        Pair(ITEM_ID_FILTER_MITAO, 0.4f),
        Pair(ITEM_ID_FILTER_YINHUA, 0.4f),
        Pair(ITEM_ID_FILTER_BEIHAIDAO, 0.4f),
        Pair(ITEM_ID_FILTER_S3, 0.4f),
    )
    // key: itemId
    private val cacheItemValueMap = mutableMapOf<Int, Float>().apply {
        put(ITEM_ID_BEAUTY_SMOOTH, defaultItemValueMap[ITEM_ID_BEAUTY_SMOOTH] ?: 0.0f)
        put(ITEM_ID_BEAUTY_WHITEN, defaultItemValueMap[ITEM_ID_BEAUTY_WHITEN] ?: 0.0f)
        put(ITEM_ID_BEAUTY_OVERALL, defaultItemValueMap[ITEM_ID_BEAUTY_OVERALL] ?: 0.0f)
        put(ITEM_ID_BEAUTY_CHEEKBONE, defaultItemValueMap[ITEM_ID_BEAUTY_CHEEKBONE] ?: 0.0f)
        put(ITEM_ID_BEAUTY_JAWBONE, defaultItemValueMap[ITEM_ID_BEAUTY_JAWBONE] ?: 0.0f)
        put(ITEM_ID_BEAUTY_EYE, defaultItemValueMap[ITEM_ID_BEAUTY_EYE] ?: 0.0f)
        put(ITEM_ID_BEAUTY_TEETH, defaultItemValueMap[ITEM_ID_BEAUTY_TEETH] ?: 0.0f)
        put(ITEM_ID_BEAUTY_FOREHEAD, defaultItemValueMap[ITEM_ID_BEAUTY_FOREHEAD] ?: 0.0f)
        put(ITEM_ID_BEAUTY_NOSE, defaultItemValueMap[ITEM_ID_BEAUTY_NOSE] ?: 0.0f)
        put(ITEM_ID_BEAUTY_MOUTH, defaultItemValueMap[ITEM_ID_BEAUTY_MOUTH] ?: 0.0f)
        put(ITEM_ID_BEAUTY_CHIN, defaultItemValueMap[ITEM_ID_BEAUTY_CHIN] ?: 0.0f)
    }
    private val cacheItemOperation = mutableMapOf<Int, ArrayList<Int>>().apply {
        put(
            GROUP_ID_BEAUTY, arrayListOf(
                ITEM_ID_BEAUTY_WHITEN,
                ITEM_ID_BEAUTY_OVERALL,
                ITEM_ID_BEAUTY_CHEEKBONE,
                ITEM_ID_BEAUTY_JAWBONE,
                ITEM_ID_BEAUTY_EYE,
                ITEM_ID_BEAUTY_TEETH,
                ITEM_ID_BEAUTY_FOREHEAD,
                ITEM_ID_BEAUTY_NOSE,
                ITEM_ID_BEAUTY_MOUTH,
                ITEM_ID_BEAUTY_CHIN,
                ITEM_ID_BEAUTY_SMOOTH,
            )
        )
    }

    fun getItemValueWithDefault(itemId: Int): Float = cacheItemValueMap[itemId] ?: defaultItemValueMap[itemId] ?: 0.0f

    fun getLastOperationItemId(groupId: Int): Int {
        return cacheItemOperation[groupId]?.lastOrNull() ?: groupId
    }

    fun getItemValue(itemId: Int): Float = cacheItemValueMap[itemId] ?: 0.0f

    internal fun cacheItemValue(groupId: Int, itemId: Int, value: Float) {
        if (itemId == groupId) {
            cacheItemValueMap.keys.toList().forEach {
                if ((it and groupId) > 0) {
                    cacheItemValueMap.remove(it)
                }
            }
            return
        }
        cacheItemValueMap[itemId] = value
    }

    internal fun cacheOperation(groupId: Int, itemId: Int) {
        if (itemId == groupId) {
            cacheItemOperation.remove(groupId)
            return
        }
        cacheItemOperation[groupId] = arrayListOf<Int>().apply {
            cacheItemOperation[groupId]?.let {
                it.removeIf { item -> item == itemId }
                addAll(it)
            }
            add(itemId)
        }
    }

    internal fun restoreByOperation(processor: IBeautyProcessor) {
        cacheItemOperation[GROUP_ID_BEAUTY]?.let { list ->
            list.lastOrNull()?.let {
                processor.setFaceBeautify(it, getItemValue(it))
            }
        }
        cacheItemOperation[GROUP_ID_FILTER]?.let { list ->
            list.lastOrNull()?.let {
                processor.setFilter(it, getItemValue(it))
            }
        }
        cacheItemOperation[GROUP_ID_EFFECT]?.let { list ->
            list.lastOrNull()?.let {
                processor.setEffect(it, getItemValue(it))
            }
        }
        cacheItemOperation[GROUP_ID_STICKER]?.let { list ->
            list.lastOrNull()?.let {
                processor.setSticker(it)
            }
        }
    }
}