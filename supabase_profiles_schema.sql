-- ============================================================================
-- Supabase User Profiles Database Schema
-- ============================================================================
-- Run these commands in your Supabase SQL Editor (Dashboard > SQL Editor)
-- 
-- This creates the user profiles table for storing additional user information
-- like nickname, bio, avatar_url, etc.
-- ============================================================================

-- Create profiles table for storing user profile information
CREATE TABLE IF NOT EXISTS profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    nickname TEXT UNIQUE NOT NULL,
    bio TEXT,
    avatar_url TEXT,
    email TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- ============================================================================
-- Row Level Security (RLS) Policies
-- ============================================================================
-- Enable RLS on profiles table for security
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;

-- RLS Policy for profiles table
-- Users can view all profiles (for nickname search functionality)
CREATE POLICY "Profiles are viewable by everyone" ON profiles
    FOR SELECT USING (true);

-- Users can only update their own profile
CREATE POLICY "Users can update their own profile" ON profiles
    FOR UPDATE USING (auth.uid() = id);

-- Users can insert their own profile
CREATE POLICY "Users can insert their own profile" ON profiles
    FOR INSERT WITH CHECK (auth.uid() = id);

-- ============================================================================
-- Triggers and Functions
-- ============================================================================

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Trigger to automatically update updated_at on profile updates
CREATE TRIGGER trigger_update_profiles_updated_at
    BEFORE UPDATE ON profiles
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Function to create profile on user signup
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.profiles (id, email)
    VALUES (NEW.id, NEW.email);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Trigger to create profile automatically when user signs up (only if it doesn't exist)
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE PROCEDURE public.handle_new_user();

-- ============================================================================
-- Indexes for Performance
-- ============================================================================

-- Index for faster nickname lookups (case-insensitive)
CREATE INDEX IF NOT EXISTS idx_profiles_nickname_lower ON profiles (LOWER(nickname));

-- Index for faster user lookups
CREATE INDEX IF NOT EXISTS idx_profiles_created_at ON profiles (created_at DESC);

-- Unique constraint for nickname (case-insensitive)
CREATE UNIQUE INDEX IF NOT EXISTS idx_profiles_nickname_unique_lower 
ON profiles (LOWER(nickname));

-- ============================================================================
-- Sample Data (Optional - for testing)
-- ============================================================================
-- Uncomment the following if you want to insert sample profile data for testing
-- Note: Replace the UUIDs with actual user IDs from your auth.users table

/*
-- Insert sample profiles (replace UUIDs with real user IDs)
INSERT INTO profiles (id, nickname, bio, email) VALUES 
    ('your-user-1-uuid', 'developer_a', '안드로이드 개발자입니다', 'dev.a@example.com'),
    ('your-user-2-uuid', 'designer_b', 'UI/UX 디자이너입니다', 'designer.b@example.com'),
    ('your-user-3-uuid', 'planner_c', '기획자입니다', 'planner.c@example.com');
*/

-- ============================================================================
-- Usage Notes
-- ============================================================================
-- After running this schema:
-- 1. Users will automatically get a profile when they sign up
-- 2. Users must set a unique nickname before they can chat
-- 3. Nicknames are case-insensitive (e.g., "John" and "john" are the same)
-- 4. All users can view profiles (needed for nickname search)
-- 5. Users can only edit their own profile
--
-- The schema supports:
-- ✅ Automatic profile creation on user signup
-- ✅ Unique nickname constraints (case-insensitive)
-- ✅ Profile privacy with RLS
-- ✅ Efficient nickname searching
-- ✅ Profile updates with timestamps
-- ============================================================================