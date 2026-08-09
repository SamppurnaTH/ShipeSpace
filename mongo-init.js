db = db.getSiblingDB('shipspace');
db.createCollection('users');
db.createCollection('projects');
db.createCollection('ships');
db.createCollection('following');
db.createCollection('score_event_log');
db.createCollection('user_interest_profiles');
