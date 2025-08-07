-- ============================================================================
-- STEP 3: Triggers and Functions
-- ============================================================================
-- Run this AFTER Step 2 completes successfully
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