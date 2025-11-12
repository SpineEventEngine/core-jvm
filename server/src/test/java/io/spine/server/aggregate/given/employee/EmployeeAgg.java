/*
 * Copyright 2022, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.server.aggregate.given.employee;

import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import io.spine.server.tuple.Triplet;

import static io.spine.server.aggregate.given.employee.Employees.salaryDecreased;

public class EmployeeAgg extends Aggregate<EmployeeId, Employee, Employee.Builder> {

    public EmployeeAgg(EmployeeId id) {
        super(id);
    }

    @Assign
    PersonEmployed handle(Employ cmd) {
        return PersonEmployed.newBuilder()
                .setEmployee(cmd.getEmployee())
                .setSalary(cmd.getSalary())
                .build();
    }

    @Apply
    private void on(PersonEmployed event) {
        builder()
                .setId(event.getEmployee())
                .setSalary(event.getSalary());
    }

    @Assign
    SalaryIncreased handle(IncreaseSalary cmd) {
        return SalaryIncreased.newBuilder()
                .setEmployee(cmd.getEmployee())
                .setAmount(cmd.getAmount())
                .build();
    }

    @Apply
    private void on(SalaryIncreased event) {
        builder()
                .setId(event.getEmployee())
                .setSalary(state().getSalary() + event.getAmount());
    }

    @Assign
    SalaryDecreased handle(DecreaseSalary cmd) {
        return SalaryDecreased.newBuilder()
                .setEmployee(cmd.getEmployee())
                .setAmount(cmd.getAmount())
                .build();
    }

    @Assign
    Triplet<SalaryDecreased, SalaryDecreased, SalaryDecreased>
    handle(DecreaseSalaryThreeTimes cmd) {
        var employee = cmd.getEmployee();
        var amount = cmd.getAmount();
        return Triplet.of(
                salaryDecreased(employee, amount),
                salaryDecreased(employee, amount),
                salaryDecreased(employee, amount)
        );
    }

    @Apply
    private void on(SalaryDecreased event) {
        builder()
                .setId(event.getEmployee())
                .setSalary(state().getSalary() - event.getAmount());
    }
}
