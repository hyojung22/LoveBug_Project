-- ============================================================================
-- STEP 2: Row Level Security (RLS) Policies
-- ============================================================================
-- Run this AFTER Step 1 completes successfully
-- ============================================================================

-- Enable RLS on both tables for security
ALTER TABLE chats ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_messages ENABLE ROW LEVEL SECURITY;

-- RLS Policy for chats table
-- Users can only see chats they are part of
CREATE POLICY "Users can view their own chats" ON chats
    FOR SELECT USING (
        auth.uid() = user1_id OR auth.uid() = user2_id
    );

CREATE POLICY "Users can create chats with themselves involved" ON chats
    FOR INSERT WITH CHECK (
        auth.uid() = user1_id OR auth.uid() = user2_id
    );

CREATE POLICY "Users can update chats they are part of" ON chats
    FOR UPDATE USING (
        auth.uid() = user1_id OR auth.uid() = user2_id
    );

-- RLS Policy for chat_messages table
-- Users can only see messages in chats they are part of
CREATE POLICY "Users can view messages in their chats" ON chat_messages
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM chats 
            WHERE chats.chat_id = chat_messages.chat_id 
            AND (chats.user1_id = auth.uid() OR chats.user2_id = auth.uid())
        )
    );

CREATE POLICY "Users can send messages in their chats" ON chat_messages
    FOR INSERT WITH CHECK (
        auth.uid() = sender_id AND
        EXISTS (
            SELECT 1 FROM chats 
            WHERE chats.chat_id = chat_messages.chat_id 
            AND (chats.user1_id = auth.uid() OR chats.user2_id = auth.uid())
        )
    );