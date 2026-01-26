#!/bin/bash

# MongoDB Database Cleanup Script
# Deletes ALL data from the jd_resume_db database
# ⚠️ THIS IS IRREVERSIBLE - USE WITH CAUTION

echo "=================================="
echo "🗑️  MongoDB Database Cleanup"
echo "=================================="
echo ""
echo "⚠️  WARNING: This will DELETE ALL data from:"
echo "   - Job Descriptions"
echo "   - Resumes"
echo "   - Match Results"
echo "   - All other collections"
echo ""
echo "This action is IRREVERSIBLE!"
echo ""
read -p "Are you sure you want to continue? (type 'YES' to confirm): " confirmation

if [ "$confirmation" != "YES" ]; then
    echo "❌ Cancelled. No data was deleted."
    exit 0
fi

echo ""
echo "🔄 Connecting to MongoDB..."

# Load MongoDB URI from .env
if [ -f .env ]; then
    export $(grep -v '^#' .env | grep MONGO_URI | xargs)
else
    echo "❌ Error: .env file not found"
    exit 1
fi

if [ -z "$MONGO_URI" ]; then
    echo "❌ Error: MONGO_URI not found in .env"
    exit 1
fi

# Create a temporary MongoDB script
cat > /tmp/drop_collections.js << 'EOJS'
// Switch to the database
db = db.getSiblingDB('jd_resume_db');

print('\n📊 Current collections:');
db.getCollectionNames().forEach(function(collection) {
    print('  - ' + collection + ' (' + db[collection].count() + ' documents)');
});

print('\n🗑️  Dropping all collections...');

// Drop all collections
db.getCollectionNames().forEach(function(collection) {
    print('  Dropping: ' + collection);
    db[collection].drop();
});

print('\n✅ All collections dropped!');
print('\n📊 Remaining collections:');
print(db.getCollectionNames().length === 0 ? '  (none - database is empty)' : db.getCollectionNames());
EOJS

# Run the MongoDB script
echo "🗑️  Deleting all collections..."
mongosh "$MONGO_URI" --file /tmp/drop_collections.js

# Clean up
rm /tmp/drop_collections.js

echo ""
echo "=================================="
echo "✅ Database cleanup complete!"
echo "=================================="
