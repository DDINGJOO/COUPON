#!/bin/bash

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 함수 정의
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# 포트 정보 출력
print_ports() {
    echo -e "\n${GREEN}=== 서비스 접속 정보 ===${NC}"
    echo -e "${BLUE}PostgreSQL:${NC}     localhost:25432"
    echo -e "${BLUE}Redis:${NC}          localhost:26379"
    echo -e "${BLUE}Kafka:${NC}          localhost:39092"
    echo -e "${BLUE}Kafka UI:${NC}       http://localhost:38080"
    echo -e "${BLUE}Redis Commander:${NC} http://localhost:38081"
    echo -e "${BLUE}pgAdmin:${NC}        http://localhost:35050"
    echo -e "\n${YELLOW}pgAdmin 로그인:${NC}"
    echo -e "  Email: admin@coupon.com"
    echo -e "  Password: admin123"
}

# 메인 메뉴
show_menu() {
    echo -e "\n${GREEN}=== 쿠폰 서비스 로컬 개발 환경 ===${NC}"
    echo "1) 전체 서비스 시작"
    echo "2) 전체 서비스 중지"
    echo "3) 전체 서비스 재시작"
    echo "4) 서비스 상태 확인"
    echo "5) 로그 보기"
    echo "6) 데이터 초기화"
    echo "7) 포트 정보 보기"
    echo "8) 종료"
    echo -n "선택: "
}

# 서비스 시작
start_services() {
    print_info "Docker Compose 서비스를 시작합니다..."
    docker-compose up -d

    if [ $? -eq 0 ]; then
        print_success "모든 서비스가 시작되었습니다!"
        print_info "서비스가 완전히 준비될 때까지 약 30초 기다려주세요..."

        # Health check
        sleep 5
        print_info "Health check 진행 중..."

        # PostgreSQL check
        docker exec coupon-postgres pg_isready -U coupon_user -d coupon_db > /dev/null 2>&1
        if [ $? -eq 0 ]; then
            print_success "PostgreSQL 준비 완료"
        else
            print_warning "PostgreSQL이 아직 준비 중입니다"
        fi

        # Redis check
        docker exec coupon-redis redis-cli ping > /dev/null 2>&1
        if [ $? -eq 0 ]; then
            print_success "Redis 준비 완료"
        else
            print_warning "Redis가 아직 준비 중입니다"
        fi

        print_ports
    else
        print_error "서비스 시작 실패!"
    fi
}

# 서비스 중지
stop_services() {
    print_info "Docker Compose 서비스를 중지합니다..."
    docker-compose down

    if [ $? -eq 0 ]; then
        print_success "모든 서비스가 중지되었습니다!"
    else
        print_error "서비스 중지 실패!"
    fi
}

# 서비스 재시작
restart_services() {
    stop_services
    sleep 2
    start_services
}

# 서비스 상태 확인
check_status() {
    print_info "Docker Compose 서비스 상태를 확인합니다..."
    docker-compose ps
}

# 로그 보기
view_logs() {
    echo -e "\n${GREEN}=== 로그 옵션 ===${NC}"
    echo "1) 전체 로그"
    echo "2) PostgreSQL 로그"
    echo "3) Redis 로그"
    echo "4) Kafka 로그"
    echo "5) 실시간 전체 로그 (Ctrl+C로 종료)"
    echo -n "선택: "
    read log_choice

    case $log_choice in
        1)
            docker-compose logs --tail=100
            ;;
        2)
            docker-compose logs --tail=100 postgres
            ;;
        3)
            docker-compose logs --tail=100 redis
            ;;
        4)
            docker-compose logs --tail=100 kafka
            ;;
        5)
            print_info "실시간 로그를 표시합니다. Ctrl+C로 종료하세요."
            docker-compose logs -f
            ;;
        *)
            print_error "잘못된 선택입니다."
            ;;
    esac
}

# 데이터 초기화
reset_data() {
    print_warning "모든 데이터가 삭제됩니다! 계속하시겠습니까? (y/n)"
    read -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        print_info "서비스를 중지하고 볼륨을 삭제합니다..."
        docker-compose down -v

        if [ $? -eq 0 ]; then
            print_success "데이터가 초기화되었습니다!"
        else
            print_error "데이터 초기화 실패!"
        fi
    else
        print_info "취소되었습니다."
    fi
}

# 메인 루프
while true; do
    show_menu
    read choice

    case $choice in
        1)
            start_services
            ;;
        2)
            stop_services
            ;;
        3)
            restart_services
            ;;
        4)
            check_status
            ;;
        5)
            view_logs
            ;;
        6)
            reset_data
            ;;
        7)
            print_ports
            ;;
        8)
            print_info "종료합니다."
            exit 0
            ;;
        *)
            print_error "잘못된 선택입니다. 다시 선택해주세요."
            ;;
    esac
done
