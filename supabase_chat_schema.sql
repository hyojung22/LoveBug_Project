-- ============================================================================
-- Supabase Real-time Chat Database Schema
-- ============================================================================
-- Run these commands in your Supabase SQL Editor (Dashboard > SQL Editor)
-- 
-- This creates the necessary tables for 1-on-1 chat functionality
-- with real-time subscriptions using Supabase Realtime
-- ============================================================================

-- Create chats table for 1-on-1 conversations
CREATE TABLE IF NOT EXISTS chats (
    chat_id SERIAL PRIMARY KEY,
    user1_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    user2_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- Create chat_messages table for individual messages
CREATE TABLE IF NOT EXISTS chat_messages (
    message_id SERIAL PRIMARY KEY,
    chat_id INTEGER NOT NULL REFERENCES chats(chat_id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    message TEXT NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- ============================================================================
-- Row Level Security (RLS) Policies
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

-- ============================================================================
-- Triggers and Functions
-- ============================================================================

-- Function to update chat updated_at timestamp when new message is sent
CREATE OR REPLACE FUNCTION update_chat_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE chats 
    SET updated_at = now() 
    WHERE chat_id = NEW.chat_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Trigger to automatically update chat timestamp on new message
CREATE TRIGGER trigger_update_chat_timestamp
    AFTER INSERT ON chat_messages
    FOR EACH ROW
    EXECUTE FUNCTION update_chat_timestamp();

-- ============================================================================
-- Enable Realtime for live updates
-- ============================================================================
-- Enable realtime subscriptions on both tables
ALTER PUBLICATION supabase_realtime ADD TABLE chats;
ALTER PUBLICATION supabase_realtime ADD TABLE chat_messages;

-- ============================================================================
-- Sample Data (Optional - for testing)
-- ============================================================================
-- Uncomment the following if you want to insert sample data for testing
-- Note: Replace the UUIDs with actual user IDs from your auth.users table

/*
-- Insert sample chat room (replace UUIDs with real user IDs)
INSERT INTO chats (user1_id, user2_id) VALUES 
    ('your-user-1-uuid', 'your-user-2-uuid');

-- Insert sample messages (replace chat_id and sender_id with real values)
INSERT INTO chat_messages (chat_id, sender_id, message, timestamp) VALUES 
    (1, 'your-user-1-uuid', 'Hello! This is a test message.', now() - interval '5 minutes'),
    (1, 'your-user-2-uuid', 'Hi there! This is a reply.', now() - interval '3 minutes'),
    (1, 'your-user-1-uuid', 'Great! The chat is working!', now() - interval '1 minute');
*/

-- ============================================================================
-- Indexes for Performance
-- ============================================================================

-- Index for faster chat lookups by users
CREATE INDEX IF NOT EXISTS idx_chats_users ON chats (user1_id, user2_id);
CREATE INDEX IF NOT EXISTS idx_chats_updated_at ON chats (updated_at DESC);

-- Index for faster message queries
CREATE INDEX IF NOT EXISTS idx_chat_messages_chat_id ON chat_messages (chat_id);
CREATE INDEX IF NOT EXISTS idx_chat_messages_created_at ON chat_messages (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_messages_chat_timestamp ON chat_messages (chat_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_messages_sender ON chat_messages (sender_id);

-- Unique index to prevent duplicate chat rooms between same users (both directions)
-- This replaces the CONSTRAINT that couldn't use functions
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_chat_users 
ON chats (LEAST(user1_id, user2_id), GREATEST(user1_id, user2_id));

-- ============================================================================
-- Usage Notes
-- ============================================================================
-- After running this schema:
-- 1. Make sure you have authenticated users in auth.users table
-- 2. Test the chat functionality in your Android app
-- 3. Check the realtime subscriptions are working
-- 4. Monitor performance and adjust indexes as needed
--
-- The schema supports:
-- ✅ 1-on-1 chat rooms between any two users
-- ✅ Real-time message delivery via Supabase Realtime
-- ✅ Row Level Security for data privacy
-- ✅ Automatic chat timestamp updates
-- ✅ Efficient querying with proper indexes
-- ✅ PostgreSQL triggers for automation
-- ============================================================================