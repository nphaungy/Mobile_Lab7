from fastapi import FastAPI, Depends, HTTPException
from fastapi.security import OAuth2PasswordRequestForm
from sqlalchemy.orm import Session

from auth import (
    create_access_token,
    get_current_user,
    hash_password,
    verify_password,
)
from database import Base, SessionLocal, engine, get_db
from models import Student, User
from schemas import StudentCreate, StudentResponse, TokenResponse

Base.metadata.create_all(bind=engine)


def seed_demo_user():
    """สร้างผู้ใช้ทดลอง student / student1234 ถ้ายังไม่มี (สำหรับ Lab เท่านั้น)"""
    db = SessionLocal()
    try:
        if db.query(User).filter(User.username == "student").first() is None:
            db.add(User(username="student", password_hash=hash_password("student1234")))
            db.commit()
    finally:
        db.close()


seed_demo_user()

app = FastAPI(title="Student Management API", version="2.0")


@app.get("/")
def root():
    return {"message": "Student Management API"}


# ---------- Login: ตรวจ username/password แล้วออก JWT ----------
@app.post("/token", response_model=TokenResponse)
def login(
    form: OAuth2PasswordRequestForm = Depends(),
    db: Session = Depends(get_db),
):
    user = db.query(User).filter(User.username == form.username).first()
    if user is None or not verify_password(form.password, user.password_hash):
        raise HTTPException(
            status_code=401,
            detail="Incorrect username or password",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return {"access_token": create_access_token(user.username), "token_type": "bearer"}


# ---------- Protected API: ต้องมี Bearer token ทุก endpoint ----------
@app.get(
    "/students",
    response_model=list[StudentResponse],
    dependencies=[Depends(get_current_user)],
)
def get_students(db: Session = Depends(get_db)):
    return db.query(Student).all()


@app.get(
    "/students/{student_id}",
    response_model=StudentResponse,
    dependencies=[Depends(get_current_user)],
)
def get_student(student_id: int, db: Session = Depends(get_db)):
    student = db.query(Student).filter(Student.id == student_id).first()
    if student is None:
        raise HTTPException(status_code=404, detail="Student not found")
    return student


@app.post(
    "/students",
    response_model=StudentResponse,
    dependencies=[Depends(get_current_user)],
)
def create_student(student: StudentCreate, db: Session = Depends(get_db)):
    new_student = Student(
        student_code=student.student_code,
        name=student.name,
        major=student.major,
    )
    db.add(new_student)
    db.commit()
    db.refresh(new_student)
    return new_student


@app.put(
    "/students/{student_id}",
    response_model=StudentResponse,
    dependencies=[Depends(get_current_user)],
)
def update_student(student_id: int, student: StudentCreate, db: Session = Depends(get_db)):
    existing = db.query(Student).filter(Student.id == student_id).first()
    if existing is None:
        raise HTTPException(status_code=404, detail="Student not found")
    existing.student_code = student.student_code
    existing.name = student.name
    existing.major = student.major
    db.commit()
    db.refresh(existing)
    return existing


@app.delete("/students/{student_id}", dependencies=[Depends(get_current_user)])
def delete_student(student_id: int, db: Session = Depends(get_db)):
    student = db.query(Student).filter(Student.id == student_id).first()
    if student is None:
        raise HTTPException(status_code=404, detail="Student not found")
    db.delete(student)
    db.commit()
    return {"message": "Student deleted"}