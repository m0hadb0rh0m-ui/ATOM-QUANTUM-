package com.atom.quantum

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val title: String,
    val updated: Long = System.currentTimeMillis(),
    val pinned: Boolean = false
)

@Entity(
    tableName = "messages",
    indices = [Index("chatId")],
    foreignKeys = [ForeignKey(
        entity = ChatEntity::class, parentColumns = ["id"], childColumns = ["chatId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val role: String,
    val content: String,
    val created: Long = System.currentTimeMillis()
)

@Dao
interface AtomDao {
    @Query("SELECT * FROM chats ORDER BY pinned DESC, updated DESC")
    fun observeChats(): Flow<List<ChatEntity>>

    @Query("SELECT id FROM chats ORDER BY updated DESC LIMIT 1")
    suspend fun mostRecentId(): String?

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY created ASC")
    fun observeMessages(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY created DESC LIMIT 24")
    suspend fun history(chatId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveChat(chat: ChatEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMessage(message: MessageEntity)

    @Query("UPDATE messages SET content = :text WHERE id = :id")
    suspend fun updateMessage(id: String, text: String)

    @Query("UPDATE chats SET updated = :updated WHERE id = :id")
    suspend fun touchChat(id: String, updated: Long = System.currentTimeMillis())

    @Query("UPDATE chats SET title = :title WHERE id = :id")
    suspend fun renameChat(id: String, title: String)

    @Query("UPDATE chats SET pinned = :pinned WHERE id = :id")
    suspend fun pinChat(id: String, pinned: Boolean)

    @Query("DELETE FROM chats WHERE id = :id")
    suspend fun deleteChat(id: String)

    @Query("DELETE FROM chats")
    suspend fun deleteAllChats()
}

@Database(entities = [ChatEntity::class, MessageEntity::class], version = 1, exportSchema = false)
abstract class AtomDatabase : RoomDatabase() {
    abstract fun dao(): AtomDao
}
