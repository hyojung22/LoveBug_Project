-- ============================================================================
-- STEP 4: Indexes and Realtime
-- ============================================================================
-- Run this AFTER Step 3 completes successfully
-- ============================================================================

-- Create indexes for performance
CREATE INDEX idx_chats_users ON chats (user1_id, user2_id);
CREATE INDEX idx_chats_updated_at ON chats (updated_at DESC);
CREATE INDEX idx_chat_messages_chat_id ON chat_messages (chat_id);
CREATE INDEX idx_chat_messages_created_at ON chat_messages (created_at DESC);
CREATE INDEX idx_chat_messages_chat_timestamp ON chat_messages (chat_id, created_at DESC);
CREATE INDEX idx_chat_messages_sender ON chat_messages (sender_id);

-- Unique index to prevent duplicate chat rooms between same users (both directions)
CREATE UNIQUE INDEX idx_unique_chat_users 
ON chats (LEAST(user1_id, user2_id), GREATEST(user1_id, user2_id));

-- Enable realtime subscriptions on both tables
ALTER PUBLICATION supabase_realtime ADD TABLE chats;
ALTER PUBLICATION supabase_realtime ADD TABLE chat_messages;