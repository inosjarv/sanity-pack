```marmaid
erDiagram
    sanity_pack_runs ||--o{ step_runs : "contains"
    sanity_pack_runs ||--o{ step_line_results : "logs execution of"
    
    step_runs ||--o{ step_lines : "defines sequence of"
    
    step_lines ||--o{ step_line_results : "executed as"
    
    api_requests ||--o{ api_responses : "receives"
    api_requests ||--o{ step_line_results : "logged in"
    api_responses ||--o{ step_line_results : "logged in"
    
    step_line_results ||--o{ verification_checks : "validated by"

    sanity_pack_runs {
        UUID sanity_pack_id PK
        VARCHAR overall_result
        INTEGER time_taken_ms
        INTEGER passed_count
        INTEGER failed_count
    }

    step_runs {
        UUID step_id PK
        UUID sanity_pack_id FK
        VARCHAR step_name
        BOOLEAN is_passed
    }

    step_lines {
        UUID step_line_id PK
        UUID step_id FK
        INTEGER line_number
        VARCHAR action_type
    }

    api_requests {
        UUID request_id PK
        TEXT request_payload
        VARCHAR media_type
        VARCHAR upstream_name
    }

    api_responses {
        UUID response_id PK
        UUID request_id FK
        TEXT response_payload
        INTEGER status_code
        BOOLEAN is_success
    }

    step_line_results {
        UUID line_result_id PK
        UUID sanity_pack_id FK
        UUID step_line_id FK
        UUID request_id FK
        UUID response_id FK
    }

    verification_checks {
        UUID verification_id PK
        UUID line_result_id FK
        VARCHAR check_category
        TEXT target_expression
        VARCHAR filter_criteria
        TEXT expected_value
        TEXT actual_value
        BOOLEAN is_passed
    }
```