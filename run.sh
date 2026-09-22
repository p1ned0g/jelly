
#!/usr/bin/env bash
set -e

DB_FILE="app/jelly.db"

case "${1:-}" in
  --reset)
    if [[ -f "$DB_FILE" ]]; then
      echo "Deleting $DB_FILE..."
      rm "$DB_FILE"
      echo "DB file deleted."
    else
      echo "$DB_FILE does not exist. Nothing to delete."
    fi
    ;;
  --keep|"")
    if [[ -f "$DB_FILE" ]]; then
      echo "Keeping existing DB: $DB_FILE"
    else
      echo "DB file does not exist. It may be created by the application."
    fi
    ;;
  *)
    echo "Usage: $0 [--reset | --keep]"
    exit 1
    ;;
esac

./gradlew run
