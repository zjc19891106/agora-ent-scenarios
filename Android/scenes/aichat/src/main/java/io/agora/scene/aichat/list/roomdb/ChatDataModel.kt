package io.agora.scene.aichat.list.roomdb

import android.content.Context
import io.agora.scene.aichat.AIChatCenter
import io.agora.scene.aichat.AILogger
import io.agora.scene.aichat.imkit.ChatClient
import io.agora.scene.aichat.imkit.EaseIM
import io.agora.scene.aichat.imkit.model.EaseProfile
import java.util.concurrent.ConcurrentHashMap

class ChatDataModel constructor(private val context: Context) {

    private val database by lazy { ChatDatabase.getDatabase(context, ChatClient.getInstance().currentUser) }

    private val contactList = ConcurrentHashMap<String, ChatUserEntity>()

    /**
     * Initialize the local database.
     */
    fun initDb() {
        if (EaseIM.isInited().not()) {
            throw IllegalStateException("EaseIM SDK must be inited before using.")
        }
        database
        resetUsersTimes()
        contactList.clear()
        val data = getAllContacts().values.map { it }
        if (data.isNotEmpty()) {
            EaseIM.updateUsersInfo(data)
        }
    }

    /**
     * Get the user data access object.
     */
    fun getUserDao(): ChatUserDao {
        if (EaseIM.isInited().not()) {
            throw IllegalStateException("EaseIM SDK must be inited before using.")
        }
        return database.userDao()
    }

    /**
     * Get all contacts from cache.
     */
    fun getAllContacts(): Map<String, EaseProfile> {
        if (contactList.isEmpty()) {
            loadContactFromDb()
        }
        return contactList.mapValues { it.value.parse() }
    }

    private fun loadContactFromDb() {
        contactList.clear()
        try {
            getUserDao().getAll().filter { it.userId != AIChatCenter.mChatUserId }.forEach {
                contactList[it.userId] = it
            }
        } catch (e: Exception) {
            AILogger.e("ChatDataModel", "loadContactFromDb error $e")
        }
    }

    /**
     * Get user by userId from local db.
     */
    fun getUser(userId: String?): ChatUserEntity? {
        if (userId.isNullOrEmpty()) {
            return null
        }
        if (contactList.containsKey(userId)) {
            return contactList[userId]
        }
        return getUserDao().getUser(userId)
    }

    fun getUsers(userIds: List<String>): List<ChatUserEntity> {
        // 如果 userIds 中所有的键都在 contactList 中
        if (userIds.all { it in contactList.keys }) {
            return userIds.mapNotNull { contactList[it] }
        }
        return getUserDao().getUsers(userIds);
    }

    /**
     * Insert user to local db.
     */
    fun insertUser(user: EaseProfile, isInsertDb: Boolean = true) {
        if (isInsertDb) {
            getUserDao().insertUser(user.parseToDbBean())
        }
        contactList[user.id] = user.parseToDbBean()
    }

    /**
     * Insert users to local db.
     */
    fun insertUsers(users: List<EaseProfile>) {
        getUserDao().insertUsers(users.map { it.parseToDbBean() })
        users.forEach {
            contactList[it.id] = it.parseToDbBean()
        }
    }

    /**
     * Update user update times.
     */
    fun updateUsersTimes(userIds: List<EaseProfile>) {
        if (userIds.isNotEmpty()) {
            userIds.map { it.id }.let { ids ->
                getUserDao().updateUsersTimes(ids)
                loadContactFromDb()
            }
        }
    }

    private fun resetUsersTimes() {
        getUserDao().resetUsersTimes()
    }

    fun clearCache() {
        contactList.clear()
    }

    /**
     * Update UIKit's user cache.
     */
    fun updateUserCache(userId: String?) {
        if (userId.isNullOrEmpty()) {
            return
        }
        val user = contactList[userId]?.parse() ?: return
        EaseIM.updateUsersInfo(mutableListOf(user))
    }
}