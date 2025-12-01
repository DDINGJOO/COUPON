-- ShedLock 테이블 생성
-- 분산 환경에서 스케줄러 중복 실행 방지를 위한 잠금 테이블
CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

COMMENT ON TABLE shedlock IS '스케줄러 분산 잠금 테이블';
COMMENT ON COLUMN shedlock.name IS '스케줄러 작업 이름';
COMMENT ON COLUMN shedlock.lock_until IS '잠금 유지 시간';
COMMENT ON COLUMN shedlock.locked_at IS '잠금 획득 시간';
COMMENT ON COLUMN shedlock.locked_by IS '잠금 획득 인스턴스';