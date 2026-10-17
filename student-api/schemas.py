from pydantic import BaseModel, ConfigDict


class StudentBase(BaseModel):
    student_code: str
    name: str
    major: str


class StudentCreate(StudentBase):
    pass


class StudentResponse(StudentBase):
    id: int

    model_config = ConfigDict(from_attributes=True)


class TokenResponse(BaseModel):
    access_token: str
    token_type: str