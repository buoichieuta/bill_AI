import { createClient } from '@supabase/supabase-js';

const supabaseUrl = 'https://oypyxgzmlwdabcwahobl.supabase.co';
const supabaseAnonKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im95cHl4Z3ptbHdkYWJjd2Fob2JsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzUxMDk1NzEsImV4cCI6MjA5MDY4NTU3MX0.wb3u4DklOjMm8KZ_fO98zsNpoV1LQ8xxI104Uh2H0jk';

export const supabase = createClient(supabaseUrl, supabaseAnonKey);
